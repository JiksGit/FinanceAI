import api from './axios'

export const getMetalPrices = () =>
  api.get('/metals/prices').then((res) => res.data)

export const getMetalHistory = (symbol, range = '3mo') =>
  api.get(`/metals/${symbol}/history`, { params: { range } }).then((res) => res.data)
