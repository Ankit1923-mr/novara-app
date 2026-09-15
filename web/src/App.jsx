import { BrowserRouter, Routes, Route } from 'react-router-dom'
import Welcome from './pages/Welcome'
import Onboarding from './pages/Onboarding'
import Scenario from './pages/Scenario'
import Chat from './pages/Chat'
import Readiness from './pages/Readiness'
import './App.css'

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Welcome />} />
        <Route path="/onboarding" element={<Onboarding />} />
        <Route path="/scenario" element={<Scenario />} />
        <Route path="/chat" element={<Chat />} />
        <Route path="/readiness" element={<Readiness />} />
      </Routes>
    </BrowserRouter>
  )
}

export default App
