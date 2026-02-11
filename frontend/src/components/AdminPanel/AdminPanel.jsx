import React, { useState, useEffect, useCallback } from 'react';
import { searchAdminApi, permissionApi } from '../../services/api';
import './AdminPanel.css';

const AdminPanel = ({ onClose }) => {
    const [stats, setStats] = useState(null);
    const [sensitivePages, setSensitivePages] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [reindexing, setReindexing] = useState(false);
    const [reindexPageId, setReindexPageId] = useState('');
    const [statusMessage, setStatusMessage] = useState(null);

    const loadData = useCallback(async () => {
        try {
            setLoading(true);
            setError(null);
            const [statsData, sensitiveData] = await Promise.all([
                searchAdminApi.getStats(),
                permissionApi.getSensitivePages(),
            ]);
            setStats(statsData);
            setSensitivePages(Array.isArray(sensitiveData) ? sensitiveData : [...sensitiveData]);
        } catch (err) {
            setError('Failed to load admin data');
            console.error(err);
        } finally {
            setLoading(false);
        }
    }, []);

    useEffect(() => {
        loadData();
    }, [loadData]);

    const handleReindexAll = async () => {
        try {
            setReindexing(true);
            setStatusMessage(null);
            await searchAdminApi.reindexAll();
            setStatusMessage('Full reindex started successfully');
            setTimeout(() => loadData(), 2000);
        } catch (err) {
            setStatusMessage('Failed to start reindex');
            console.error(err);
        } finally {
            setReindexing(false);
        }
    };

    const handleReindexPage = async () => {
        if (!reindexPageId.trim()) {
            alert('Please enter a page ID');
            return;
        }
        try {
            setReindexing(true);
            setStatusMessage(null);
            await searchAdminApi.reindexPage(reindexPageId.trim());
            setStatusMessage(`Reindex started for page ${reindexPageId}`);
            setReindexPageId('');
        } catch (err) {
            setStatusMessage('Failed to reindex page');
            console.error(err);
        } finally {
            setReindexing(false);
        }
    };

    return (
        <div className="modal-overlay" onClick={onClose}>
            <div className="modal admin-panel-modal" onClick={(e) => e.stopPropagation()}>
                <h2>Admin Panel</h2>

                {loading ? (
                    <div className="admin-loading">Loading...</div>
                ) : error ? (
                    <div className="admin-error">{error}</div>
                ) : (
                    <>
                        {/* Search Index Stats */}
                        <div className="admin-section">
                            <h3>Search Index Statistics</h3>
                            {stats && (
                                <div className="stats-grid">
                                    <div className="stat-card">
                                        <div className="stat-value">{stats.totalAttachments ?? stats.total ?? '-'}</div>
                                        <div className="stat-label">Total</div>
                                    </div>
                                    <div className="stat-card stat-indexed">
                                        <div className="stat-value">{stats.indexedAttachments ?? stats.indexed ?? '-'}</div>
                                        <div className="stat-label">Indexed</div>
                                    </div>
                                    <div className="stat-card stat-pending">
                                        <div className="stat-value">{stats.pendingAttachments ?? stats.pending ?? '-'}</div>
                                        <div className="stat-label">Pending</div>
                                    </div>
                                    <div className="stat-card stat-failed">
                                        <div className="stat-value">{stats.failedAttachments ?? stats.failed ?? '-'}</div>
                                        <div className="stat-label">Failed</div>
                                    </div>
                                </div>
                            )}
                        </div>

                        {/* Reindex Actions */}
                        <div className="admin-section">
                            <h3>Reindex Actions</h3>
                            {statusMessage && (
                                <div className="admin-status-message">{statusMessage}</div>
                            )}
                            <div className="reindex-actions">
                                <button
                                    className="btn btn-primary btn-sm"
                                    onClick={handleReindexAll}
                                    disabled={reindexing}
                                >
                                    {reindexing ? 'Reindexing...' : 'Reindex All'}
                                </button>
                                <div className="reindex-page-row">
                                    <input
                                        type="text"
                                        placeholder="Page ID"
                                        value={reindexPageId}
                                        onChange={(e) => setReindexPageId(e.target.value)}
                                        className="reindex-input"
                                        onKeyDown={(e) => {
                                            if (e.key === 'Enter') handleReindexPage();
                                        }}
                                    />
                                    <button
                                        className="btn btn-secondary btn-sm"
                                        onClick={handleReindexPage}
                                        disabled={reindexing || !reindexPageId.trim()}
                                    >
                                        Reindex Page
                                    </button>
                                </div>
                            </div>
                        </div>

                        {/* Sensitive Pages */}
                        <div className="admin-section">
                            <h3>Sensitive Pages</h3>
                            {sensitivePages.length === 0 ? (
                                <p className="admin-empty">No sensitive pages.</p>
                            ) : (
                                <div className="sensitive-pages-list">
                                    {sensitivePages.map((id) => (
                                        <span key={id} className="sensitive-page-badge">
                                            Page #{id}
                                        </span>
                                    ))}
                                </div>
                            )}
                        </div>
                    </>
                )}

                <div className="modal-actions">
                    <button className="btn btn-secondary" onClick={onClose}>Close</button>
                </div>
            </div>
        </div>
    );
};

export default AdminPanel;
