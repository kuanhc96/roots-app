export const useSimpleResourceClient = () => {
  const config = useRuntimeConfig()
  const accessToken = import.meta.client ? (sessionStorage.getItem('access_token') ?? undefined) : undefined
  return new SimpleResourceClient(config.public.simpleResourceServerUrl, accessToken)
}
