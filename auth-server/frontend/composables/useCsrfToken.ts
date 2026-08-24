import { getCsrfTokenFromCookie } from '~/utils/readCookies'

export const useCsrfToken = () => {
  const csrfToken = ref('')
  onMounted(() => {
    csrfToken.value = getCsrfTokenFromCookie()
  })
  return { csrfToken }
}
