import { errorMessages } from '~/utils/errorMessages'

// Reads the `error` code the auth-server attaches when it redirects to a page
// (e.g. /login?error=invalidLogin) and returns its display text, or undefined when
// there is no (known) code. Flash semantics: the code is scrubbed from the URL after
// mount so a refresh or a later visit shows a clean page.
export function useServerErrorMessage(): string | undefined {
  const route = useRoute()
  const router = useRouter()

  const code = route.query.error as string | undefined
  const message = code ? errorMessages[code] : undefined

  onMounted(() => {
    if (route.query.error) {
      const { error, ...rest } = route.query
      router.replace({ query: rest })
    }
  })

  return message
}
