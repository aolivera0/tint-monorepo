import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import CreateRoom from './pages/CreateRoom';
import InviteLanding from './pages/InviteLanding';
import WatchRoom from './pages/WatchRoom';
import ProtectedRoute from './routes/ProtectedRoute';
//import Demo from './pages/Demo'; // opcional: mover vista original aquí

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/invite/:code" element={<InviteLanding />} />
        <Route path="/dashboard" element={<ProtectedRoute><Dashboard /></ProtectedRoute>} />
        <Route path="/rooms/new" element={<ProtectedRoute><CreateRoom /></ProtectedRoute>} />
        <Route path="/rooms/:id" element={<ProtectedRoute><CreateRoom /></ProtectedRoute>} />
        <Route path="/watch/:roomId" element={<WatchRoom />} />
        {/* <Route path="/demo" element={<Demo />} /> */}
        <Route path="/" element={<Navigate to="/login" replace />} />
      </Routes>
    </BrowserRouter>
  );
}