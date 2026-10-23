use ciborium::value::Value;
use std::io::Cursor;
use wasm_bindgen::prelude::*;

fn cose_value(payload: Vec<u8>, kid: Vec<u8>, signature: Vec<u8>) -> Value {
    let protected = Value::Map(vec![(Value::Integer(1.into()), Value::Integer((-8).into()))]);
    Value::Array(vec![
        Value::Bytes(encode_value(protected.clone())),
        Value::Map(vec![(Value::Integer(4.into()), Value::Bytes(kid))]),
        Value::Bytes(payload),
        Value::Bytes(signature),
    ])
}

fn encode_value(value: Value) -> Vec<u8> {
    let mut output = Vec::new();
    ciborium::ser::into_writer(&value, &mut output).expect("CBOR encoding is infallible for Value");
    output
}

fn bytes(value: &Value) -> Result<&[u8], String> {
    match value { Value::Bytes(data) => Ok(data), _ => Err("cose_structure_invalid".into()) }
}

fn decode_sign1(encoded: &[u8]) -> Result<(Vec<u8>, Vec<u8>, Vec<u8>, Vec<u8>), String> {
    if encoded.len() > 5_000_000 { return Err("cose_structure_invalid".into()); }
    let mut cursor = Cursor::new(encoded);
    let value: Value = ciborium::de::from_reader(&mut cursor).map_err(|_| "cose_structure_invalid")?;
    if cursor.position() as usize != encoded.len() { return Err("cose_structure_invalid".into()); }
    let fields = match value { Value::Array(fields) if fields.len() == 4 => fields, _ => return Err("cose_structure_invalid".into()) };
    let protected = bytes(&fields[0])?.to_vec();
    let protected_value: Value = ciborium::de::from_reader(Cursor::new(&protected)).map_err(|_| "cose_protected_header_invalid")?;
    match protected_value {
        Value::Map(ref map) if map.len() == 1 && map[0].0 == Value::Integer(1.into()) && map[0].1 == Value::Integer((-8).into())
            && encode_value(Value::Map(map.clone())) == protected => (),
        _ => return Err("cose_algorithm_invalid".into()),
    }
    let kid = match &fields[1] {
        Value::Map(map) if map.len() == 1 && map[0].0 == Value::Integer(4.into()) => bytes(&map[0].1)?.to_vec(),
        _ => return Err("cose_structure_invalid".into()),
    };
    let payload = bytes(&fields[2])?.to_vec();
    let signature = bytes(&fields[3])?.to_vec();
    if kid.is_empty() || kid.len() > 128 || signature.len() != 64 { return Err("cose_structure_invalid".into()); }
    Ok((protected, kid, payload, signature))
}

fn sig_structure(protected: Vec<u8>, payload: Vec<u8>) -> Vec<u8> {
    encode_value(Value::Array(vec![
        Value::Text("Signature1".into()),
        Value::Bytes(protected),
        Value::Bytes(Vec::new()),
        Value::Bytes(payload),
    ]))
}

#[wasm_bindgen]
pub fn signature_input(payload: &[u8]) -> Vec<u8> {
    let protected = encode_value(Value::Map(vec![(Value::Integer(1.into()), Value::Integer((-8).into()))]));
    sig_structure(protected, payload.to_vec())
}

#[wasm_bindgen]
pub fn encode_sign1(payload: &[u8], signature: &[u8], key_id: &[u8]) -> Vec<u8> {
    encode_value(cose_value(payload.to_vec(), key_id.to_vec(), signature.to_vec()))
}

#[wasm_bindgen]
pub fn inspect_sign1(encoded: &[u8]) -> Result<Vec<u8>, JsValue> {
    decode_sign1(encoded).map(|(_, _, payload, _)| payload).map_err(|e| JsValue::from_str(&e))
}

#[wasm_bindgen]
pub fn inspect_signature(encoded: &[u8]) -> Result<Vec<u8>, JsValue> {
    decode_sign1(encoded).map(|(_, _, _, signature)| signature).map_err(|e| JsValue::from_str(&e))
}

#[wasm_bindgen]
pub fn inspect_key_id(encoded: &[u8]) -> Result<Vec<u8>, JsValue> {
    decode_sign1(encoded).map(|(_, key_id, _, _)| key_id).map_err(|e| JsValue::from_str(&e))
}

#[cfg(test)]
mod tests {
    use super::*;
    use ed25519_dalek::{Signer, SigningKey};

    #[test]
    fn emits_and_reads_cose_sign1_ed25519() {
        let key = SigningKey::from_bytes(&[7u8; 32]);
        let payload = br#"{"version":1,"event":"event-a"}"#;
        let input = signature_input(payload);
        let signature = key.sign(&input);
        let cose = encode_sign1(payload, &signature.to_bytes(), b"event-a-k1");
        let (protected, kid, decoded, sig) = decode_sign1(&cose).unwrap();
        assert_eq!(decoded, payload);
        assert_eq!(kid, b"event-a-k1");
        let rebuilt = sig_structure(protected, decoded);
        key.verifying_key().verify_strict(&rebuilt, &ed25519_dalek::Signature::from_slice(&sig).unwrap()).unwrap();
    }

    #[test]
    fn rejects_wrong_algorithm_header() {
        let value = Value::Array(vec![Value::Bytes(vec![0xa1, 0x01, 0x26]), Value::Map(vec![]), Value::Bytes(vec![]), Value::Bytes(vec![])]);
        assert_eq!(decode_sign1(&encode_value(value)).unwrap_err(), "cose_algorithm_invalid");
    }
}
