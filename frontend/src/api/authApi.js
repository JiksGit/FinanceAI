import api from './axios'

export const signup = (data) => api.post('/auth/signup', data).then((res) => res.data)

export const login = (data) => api.post('/auth/login', data).then((res) => res.data)

export const refresh = (refreshToken) =>
  api.post('/auth/refresh', { refreshToken }).then((res) => res.data)

export const logoutApi = (refreshToken) =>
  api.post('/auth/logout', { refreshToken }).catch(() => {})
