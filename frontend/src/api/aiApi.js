import api from './axios'

export const analyze = (question, includeExchangeRate, includeStock) =>
  api
    .post('/ai/analyze', { question, includeExchangeRate, includeStock })
    .then((res) => res.data)

export const getChatHistory = () => api.get('/ai/history').then((res) => res.data)

export const clearChatHistory = () => api.delete('/ai/history')
