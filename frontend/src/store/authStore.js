import { create } from 'zustand'

const ACCESS_TOKEN_KEY = 'accessToken'
const REFRESH_TOKEN_KEY = 'refreshToken'
const NICKNAME_KEY = 'nickname'

export const useAuthStore = create((set) => ({
  accessToken: localStorage.getItem(ACCESS_TOKEN_KEY),
  refreshToken: localStorage.getItem(REFRESH_TOKEN_KEY),
  nickname: localStorage.getItem(NICKNAME_KEY),

  login: ({ accessToken, refreshToken, nickname }) => {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
    if (nickname) localStorage.setItem(NICKNAME_KEY, nickname)
    set({ accessToken, refreshToken, nickname })
  },

  setTokens: ({ accessToken, refreshToken }) => {
    localStorage.setItem(ACCESS_TOKEN_KEY, accessToken)
    localStorage.setItem(REFRESH_TOKEN_KEY, refreshToken)
    set({ accessToken, refreshToken })
  },

  logout: () => {
    localStorage.removeItem(ACCESS_TOKEN_KEY)
    localStorage.removeItem(REFRESH_TOKEN_KEY)
    localStorage.removeItem(NICKNAME_KEY)
    set({ accessToken: null, refreshToken: null, nickname: null })
  },
}))
