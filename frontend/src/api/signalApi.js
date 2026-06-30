import api from './axios'

export const getRecentSignals = () => api.get('/signals').then((res) => res.data)

export const getMySignals = () => api.get('/signals/my').then((res) => res.data)

export const generateSignals = () => api.post('/signals/generate').then((res) => res.data)
