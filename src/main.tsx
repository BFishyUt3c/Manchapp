import React from 'react'
import ReactDOM from 'react-dom/client'
import MobileApp from './MobileApp'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <MobileApp />
  </React.StrictMode>,
)

if ('serviceWorker' in navigator) navigator.serviceWorker.getRegistrations().then(registrations => registrations.forEach(registration => void registration.unregister())).catch(() => {});
