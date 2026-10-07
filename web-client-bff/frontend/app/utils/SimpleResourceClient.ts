import axios, { type AxiosInstance } from 'axios'

// Calls go to this app's own same-origin /api/role/* endpoints. The browser sends
// only the __Host-SESSION cookie; the bff looks up (or refreshes) the session's
// access token and forwards the call through gateway-server to simple-resource-server.
export class SimpleResourceClient {
  private readonly http: AxiosInstance

  constructor(baseUrl: string) {
    this.http = axios.create({
      baseURL: baseUrl,
    })

    this.http.interceptors.response.use(
      (response) => response,
      (error) => {
        if (error.response?.status === 401) {
          window.location.href = `/session-expired`
        }
        return Promise.reject(error)
      },
    )
  }

  getPastor() {
    return this.http.get<string>('/role/pastor')
  }

  getDeacon() {
    return this.http.get<string>('/role/deacon')
  }

  getSmallGroupLeader() {
    return this.http.get<string>('/role/small-group-leader')
  }

  getViceSmallGroupLeader() {
    return this.http.get<string>('/role/vice-small-group-leader')
  }

  getMember() {
    return this.http.get<string>('/role/member')
  }

  getGuest() {
    return this.http.get<string>('/role/guest')
  }
}
