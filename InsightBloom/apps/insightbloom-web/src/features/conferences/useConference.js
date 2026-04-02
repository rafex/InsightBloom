import { ref } from 'vue'
import { getConferenceByFriendlyId } from '@/services/api/usersApi'

export function useConference(friendlyId) {
  const conference = ref(null)
  const loading = ref(false)
  const error = ref('')

  async function load() {
    loading.value = true
    error.value = ''
    try {
      conference.value = await getConferenceByFriendlyId(friendlyId)
    } catch (e) {
      error.value = 'Conferencia no encontrada'
    } finally {
      loading.value = false
    }
  }

  return { conference, loading, error, load }
}
