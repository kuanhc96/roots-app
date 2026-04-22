export const useSimpleResourceClient = () => {
  const config = useRuntimeConfig()
  return new SimpleResourceClient(config.public.simpleResourceServerUrl)
}
