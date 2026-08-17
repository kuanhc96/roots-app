import axios, { type AxiosInstance } from 'axios'

// Calls go through gateway-server's /simple-resource-server prefix. The browser
// sends only the __Host-SESSION cookie; gateway-server resolves and injects the
// bearer token from Redis before proxying to simple-resource-server.
export class SimpleResourceClient {
  private readonly http: AxiosInstance

  constructor(baseUrl: string) {
    this.http = axios.create({
      baseURL: baseUrl,
      withCredentials: true,
    })
  }

  getPastor() {
    return this.http.get<string>('/api/role/pastor')
  }

  getDeacon() {
    return this.http.get<string>('/api/role/deacon')
  }

  getSmallGroupLeader() {
    return this.http.get<string>('/api/role/small-group-leader')
  }

  getViceSmallGroupLeader() {
    return this.http.get<string>('/api/role/vice-small-group-leader')
  }

  getMember() {
    return this.http.get<string>('/api/role/member')
  }

  getGuest() {
    return this.http.get<string>('/api/role/guest')
  }
}
