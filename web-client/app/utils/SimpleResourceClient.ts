import axios, { type AxiosInstance } from 'axios'

export class SimpleResourceClient {
  private readonly http: AxiosInstance

  constructor(baseUrl: string, accessToken?: string) {
    this.http = axios.create({
      baseURL: baseUrl,
      headers: accessToken ? { Authorization: `Bearer ${accessToken}` } : {},
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
