import api from './axios'

export const searchStock = (keyword) =>
  api.get('/stock/search', { params: { keyword } }).then((res) => res.data)

export const getStock = (symbol) => api.get(`/stock/${symbol}`).then((res) => res.data)

export const getStockHistory = (symbol, days = 30) =>
  api.get(`/stock/${symbol}/history`, { params: { days } }).then((res) => res.data)

export const getFavorites = () => api.get('/stock/favorites').then((res) => res.data)

export const addFavorite = (symbol, name) =>
  api.post('/stock/favorites', { stockSymbol: symbol, stockName: name }).then((res) => res.data)

export const removeFavorite = (symbol) => api.delete(`/stock/favorites/${symbol}`)

export const updateHolding = (symbol, quantity, avgPrice) =>
  api.put(`/stock/favorites/${symbol}/holding`, { quantity, avgPrice })

export const getPortfolioSummary = () =>
  api.get('/stock/portfolio/summary').then((res) => res.data)

export const getStockNews = (symbol) =>
  api.get(`/stock/${symbol}/news`).then((res) => res.data)

export const getTopStocks = (market = '', limit = 50) =>
  api.get('/stock/market/top', { params: { market, limit } }).then((res) => res.data)
