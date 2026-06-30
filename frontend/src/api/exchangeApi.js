import api from './axios'

export const getTodayRates = () => api.get('/exchange/today').then((res) => res.data)

export const getRateHistory = (currency, days = 30) =>
  api.get('/exchange/history', { params: { currency, days } }).then((res) => res.data)
