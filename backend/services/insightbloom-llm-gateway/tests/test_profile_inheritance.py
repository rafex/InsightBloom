import base64
import hashlib
import hmac
import json
import sqlite3
import pytest
from fastapi.testclient import TestClient
from app import main as gateway
from app.crypto import KeyRing
from app.store import CredentialStore


def ring(version='v1'):
    return KeyRing(version, {'v1': b'a'*32, 'v2': b'b'*32})


@pytest.fixture
def setup(tmp_path, monkeypatch):
    store = CredentialStore(str(tmp_path/'gateway.sqlite3'), ring())
    monkeypatch.setattr(gateway, 'store', store)
    monkeypatch.setenv('LLM_USERS_RESOLUTION_URL', 'http://users/internal/llm-resolution')
    monkeypatch.setenv('LLM_GATEWAY_USERS_TOKEN', 'users-token')
    monkeypatch.setenv('LLM_GATEWAY_CHAT_TOKEN', 'chat-token')
    monkeypatch.setenv('LLM_GATEWAY_CONTEXT_SIGNING_KEY', 'x'*40)
    ctx = {'mode': 'AUTO', 'ownerId': 'owner', 'ownerEligible': True}
    class Response:
        def raise_for_status(self): pass
        def json(self): return {'data': dict(ctx)}
    calls = []
    def request(url, **kwargs):
        calls.append((url, kwargs)); return Response()
    monkeypatch.setattr(gateway.httpx, 'get', request)
    return store, ctx, TestClient(gateway.app), calls


def save(store, scope, identity, key='sensitive-key', cap='chat'):
    store.set(scope_type=scope, scope_id=identity, capability=cap, provider='openai', model='gpt-4o-mini', api_key=key, actor=identity)


def test_dynamic_inheritance_existing_override_modes_and_isolation(setup):
    store, ctx, client, calls = setup
    save(store,'user','owner','owner-v1')
    a=gateway._effective_status('event-a','chat'); b=gateway._effective_status('event-b','chat')
    assert a['mode']=='INHERIT_OWNER' and a['effectiveSource']=='OWNER' and b['scopeId']=='owner'
    save(store,'user','owner','owner-v2')
    assert store.resolve(event_id='event-a',capability='chat',scope_type=a['scopeType'],scope_id=a['scopeId'])[2]=='owner-v2'
    save(store,'event','event-a','event-only')
    assert gateway._effective_status('event-a','chat')['mode']=='EVENT'
    assert gateway._effective_status('event-b','chat')['effectiveSource']=='OWNER'
    ctx['mode']='DISABLED'
    assert not gateway._effective_status('event-a','chat')['configured']
    ctx['mode']='INHERIT_OWNER'
    assert gateway._effective_status('event-a','chat')['scopeId']=='owner'
    assert not gateway._effective_status('event-a','email')['configured']
    assert all(k['follow_redirects'] is False for _,k in calls)


@pytest.mark.parametrize('reason',['permission','suspension','role','withdrawal','missing_key'])
def test_linked_profile_revocation_falls_back_to_owner_or_disabled(setup,reason):
    store, ctx, _, _=setup
    save(store,'user','owner');save(store,'user','member','member-key')
    ctx.update(mode='MEMBER_PROFILE',memberId='member',memberEligible=True)
    assert gateway._effective_status('event','chat')['scopeId']=='member'
    if reason=='missing_key': store.clear(scope_type='user',scope_id='member',capability='chat',actor='member')
    elif reason=='withdrawal': ctx['mode']='INHERIT_OWNER'
    else: ctx['memberEligible']=False
    result=gateway._effective_status('event','chat')
    assert result['scopeId']=='owner' and result['effectiveSource']=='OWNER'
    ctx['ownerEligible']=False
    assert not gateway._effective_status('event','chat')['configured']


def test_personal_ciphertext_restart_rotation_preservation_and_deletion(tmp_path):
    path=str(tmp_path/'credentials.sqlite3');store=CredentialStore(path,ring());save(store,'user','owner','private-personal-key')
    raw=sqlite3.connect(path).execute('SELECT ciphertext FROM user_credentials').fetchone()[0]
    assert 'private-personal-key' not in raw
    restarted=CredentialStore(path,ring('v2'))
    assert restarted.reencrypt_all()==1
    assert restarted.resolve(event_id='event',capability='chat',scope_type='user',scope_id='owner')[2]=='private-personal-key'
    restarted.set(scope_type='user',scope_id='owner',capability='chat',provider='openai',model='gpt-4o-mini',api_key='',actor='owner')
    with pytest.raises(ValueError,match='new_api_key_required'):
        restarted.set(scope_type='user',scope_id='owner',capability='chat',provider='groq',model='x',api_key='',actor='owner')
    assert restarted.status(scope_type='user',scope_id='other',capability='chat')=={'configured':False}
    restarted.clear(scope_type='user',scope_id='owner',capability='chat',actor='owner')
    assert restarted.resolve(event_id='event',capability='chat',scope_type='user',scope_id='owner') is None


def presentation():
    payload=base64.urlsafe_b64encode(json.dumps({'sub':'event','event':'event','capabilities':['chat'],'exp':4000000000}).encode()).decode().rstrip('=')
    sig=base64.urlsafe_b64encode(hmac.new(b'x'*40,payload.encode(),hashlib.sha256).digest()).decode().rstrip('=')
    return {'organizerId':'event','eventId':'event','capability':'chat','contextToken':payload+'.'+sig,'messages':[{'role':'user','content':'hello'}]}


def test_provider_error_never_spends_owner_fallback_and_resolution_outage_fails_closed(setup,monkeypatch):
    store,ctx,client,_=setup
    save(store,'event','event','bad-event-key');save(store,'user','owner','good-owner-key')
    attempts=[]
    def provider(url,key,payload): attempts.append(key);raise ValueError('unauthorized')
    monkeypatch.setattr(gateway,'_post_provider',provider)
    headers={'X-Service-Id':'chat','X-Service-Token':'chat-token'}
    result=client.post('/internal/complete',json=presentation(),headers=headers)
    assert result.status_code==502 and attempts==['bad-event-key']
    def outage(*args,**kwargs): raise OSError('unreachable')
    monkeypatch.setattr(gateway.httpx,'get',outage)
    assert client.post('/internal/complete',json=presentation(),headers=headers).status_code==503
    assert attempts==['bad-event-key']


def test_simulated_two_events_owner_moderator_and_unrelated_organizer(setup,monkeypatch):
    store,ctx,client,_=setup
    save(store,'user','owner','owner-key');save(store,'user','moderator','moderator-key');save(store,'user','unrelated','unrelated-key')
    attempts=[]
    monkeypatch.setattr(gateway,'_post_provider',lambda url,key,payload: attempts.append(key) or {'choices':[{'message':{'content':'mock response'}}]})
    headers={'X-Service-Id':'chat','X-Service-Token':'chat-token'}
    assert client.post('/internal/complete',json=presentation(),headers=headers).status_code==200
    ctx.update(mode='MEMBER_PROFILE',memberId='moderator',memberEligible=True)
    assert client.post('/internal/complete',json=presentation(),headers=headers).status_code==200
    ctx['memberEligible']=False
    assert client.post('/internal/complete',json=presentation(),headers=headers).status_code==200
    assert attempts==['owner-key','moderator-key','owner-key']
    assert 'unrelated-key' not in attempts
    rows=sqlite3.connect(store.database).execute('SELECT credential_owner FROM credential_usage').fetchall()
    assert ('owner',) in rows and ('moderator',) in rows
    status=client.get('/internal/effective/event/chat',headers={'X-Service-Id':'users','X-Service-Token':'users-token'})
    assert all(secret not in status.text for secret in ['owner-key','moderator-key','unrelated-key','ciphertext','apiKey'])
