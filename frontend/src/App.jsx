import { Route, Routes } from 'react-router-dom'
import Navbar from './components/common/Navbar'
import ProtectedRoute from './components/common/ProtectedRoute'
import HomePage from './pages/HomePage'
import StockPage from './pages/StockPage'
import SignalsPage from './pages/SignalsPage'
import AiPage from './pages/AiPage'
import LoginPage from './pages/LoginPage'

function App() {
  return (
    <div className="min-h-screen bg-slate-50">
      <Navbar />
      <main className="mx-auto max-w-6xl px-6 py-8">
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/stocks" element={<StockPage />} />
          <Route path="/signals" element={<SignalsPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route
            path="/ai"
            element={
              <ProtectedRoute>
                <AiPage />
              </ProtectedRoute>
            }
          />
        </Routes>
      </main>
    </div>
  )
}

export default App
