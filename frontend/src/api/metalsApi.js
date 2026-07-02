import api from './axios'

export const getMetalPrices = () =>
  api.get('/metals/prices').then((res) => res.data)
