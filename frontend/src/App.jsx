import React, { useState, useEffect } from 'react';
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import WikiPage from './pages/WikiPage';
import LoginPage from './pages/LoginPage';
import AdminPanel from './components/AdminPanel/AdminPanel';
import { authApi, adminApi } from './services/api';
import './App.css';

function App() {
    const [isAuthenticated, setIsAuthenticated] = useState(authApi.isLoggedIn());
    const [isAdmin, setIsAdmin] = useState(false);
    const [showAdminPanel, setShowAdminPanel] = useState(false);

    useEffect(() => {
        if (isAuthenticated) {
            adminApi.getMe()
                .then((data) => {
                    const roles = data.roles || [];
                    setIsAdmin(roles.includes('ROLE_ADMIN'));
                })
                .catch(() => {
                    setIsAdmin(false);
                });
        } else {
            setIsAdmin(false);
        }
    }, [isAuthenticated]);

    const handleLogin = () => {
        setIsAuthenticated(true);
    };

    const handleLogout = () => {
        authApi.logout();
        setIsAuthenticated(false);
        setIsAdmin(false);
    };

    if (!isAuthenticated) {
        return <LoginPage onLogin={handleLogin} />;
    }

    return (
        <Router>
            <div className="app">
                <div className="app-topbar">
                    <span className="app-user">Logged in as <strong>{authApi.getUsername()}</strong></span>
                    {isAdmin && (
                        <button
                            className="btn btn-sm admin-btn"
                            onClick={() => setShowAdminPanel(true)}
                        >
                            Admin
                        </button>
                    )}
                    <button className="btn btn-secondary btn-sm" onClick={handleLogout}>Logout</button>
                </div>
                <Routes>
                    <Route path="/" element={<WikiPage isAdmin={isAdmin} />} />
                    <Route path="/page/:slug" element={<WikiPage isAdmin={isAdmin} />} />
                    <Route path="*" element={<Navigate to="/" replace />} />
                </Routes>
            </div>
            {showAdminPanel && (
                <AdminPanel onClose={() => setShowAdminPanel(false)} />
            )}
        </Router>
    );
}

export default App;
