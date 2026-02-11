import React, { useState, useEffect, useCallback } from 'react';
import { wikiPageApi } from '../../services/api';
import './PageTreeSidebar.css';

/**
 * TreeNode Component
 * Renders a single node in the page tree with expand/collapse functionality
 */
const FolderIcon = ({ size = 14 }) => (
    <svg
        xmlns="http://www.w3.org/2000/svg"
        width={size}
        height={size}
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
    >
        <path d="M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z" />
    </svg>
);

const PageIcon = ({ size = 14 }) => (
    <svg
        xmlns="http://www.w3.org/2000/svg"
        width={size}
        height={size}
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        strokeWidth="2"
    >
        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
        <polyline points="14 2 14 8 20 8" />
        <line x1="16" y1="13" x2="8" y2="13" />
        <line x1="16" y1="17" x2="8" y2="17" />
    </svg>
);

const TreeNode = ({ page, level = 0, selectedPageId, onSelectPage, onCreateChild }) => {
    const [isExpanded, setIsExpanded] = useState(level < 2);
    const hasChildren = page.children && page.children.length > 0;
    const isSelected = page.id === selectedPageId;
    const isFolder = page.folder;

    const handleToggle = (e) => {
        e.stopPropagation();
        setIsExpanded(!isExpanded);
    };

    const handleSelect = () => {
        if (isFolder) {
            setIsExpanded(!isExpanded);
        }
        onSelectPage(page);
    };

    const handleCreateChildPage = (e) => {
        e.stopPropagation();
        onCreateChild(page.id, false);
    };

    const handleCreateChildFolder = (e) => {
        e.stopPropagation();
        onCreateChild(page.id, true);
    };

    return (
        <div className="tree-node">
            <div
                className={`tree-node-content ${isSelected ? 'selected' : ''} ${isFolder ? 'is-folder' : ''}`}
                style={{ paddingLeft: `${level * 16 + 8}px` }}
                onClick={handleSelect}
            >
                <button
                    className={`expand-btn ${hasChildren ? 'has-children' : ''} ${
                        isExpanded ? 'expanded' : ''
                    }`}
                    onClick={handleToggle}
                    disabled={!hasChildren && !isFolder}
                >
                    {hasChildren || isFolder ? (
                        <svg
                            xmlns="http://www.w3.org/2000/svg"
                            width="12"
                            height="12"
                            viewBox="0 0 24 24"
                            fill="none"
                            stroke="currentColor"
                            strokeWidth="2"
                        >
                            <polyline points="9 18 15 12 9 6" />
                        </svg>
                    ) : (
                        <span className="dot">&bull;</span>
                    )}
                </button>
                <span className="page-icon">
                    {isFolder ? <FolderIcon /> : <PageIcon />}
                </span>
                <span className="page-title" title={page.title}>
                    {page.title}
                </span>
                <button
                    className="add-child-btn"
                    onClick={handleCreateChildPage}
                    title="Add child page"
                >
                    <svg
                        xmlns="http://www.w3.org/2000/svg"
                        width="12"
                        height="12"
                        viewBox="0 0 24 24"
                        fill="none"
                        stroke="currentColor"
                        strokeWidth="2"
                    >
                        <line x1="12" y1="5" x2="12" y2="19" />
                        <line x1="5" y1="12" x2="19" y2="12" />
                    </svg>
                </button>
                <button
                    className="add-child-btn add-folder-btn"
                    onClick={handleCreateChildFolder}
                    title="Add child folder"
                >
                    <FolderIcon size={12} />
                </button>
            </div>
            {(hasChildren || isFolder) && isExpanded && (
                <div className="tree-children">
                    {hasChildren && page.children.map((child) => (
                        <TreeNode
                            key={child.id}
                            page={child}
                            level={level + 1}
                            selectedPageId={selectedPageId}
                            onSelectPage={onSelectPage}
                            onCreateChild={onCreateChild}
                        />
                    ))}
                </div>
            )}
        </div>
    );
};

/**
 * PageTreeSidebar Component
 * Displays a hierarchical tree of all wiki pages
 */
const PageTreeSidebar = ({
    selectedPageId,
    onSelectPage,
    onCreatePage,
    refreshTrigger,
    style,
}) => {
    const [pages, setPages] = useState([]);
    const [isLoading, setIsLoading] = useState(true);
    const [error, setError] = useState(null);
    const [searchQuery, setSearchQuery] = useState('');
    const [searchResults, setSearchResults] = useState(null);
    const [isSearching, setIsSearching] = useState(false);

    // Fetch page tree
    const fetchPageTree = useCallback(async () => {
        try {
            setIsLoading(true);
            setError(null);
            const tree = await wikiPageApi.getPageTree();
            setPages(tree);
        } catch (err) {
            console.error('Error fetching page tree:', err);
            setError('Failed to load pages');
        } finally {
            setIsLoading(false);
        }
    }, []);

    // Initial fetch and refresh on trigger change
    useEffect(() => {
        fetchPageTree();
    }, [fetchPageTree, refreshTrigger]);

    // Search pages
    const handleSearch = useCallback(
        async (query) => {
            setSearchQuery(query);

            if (!query.trim()) {
                setSearchResults(null);
                return;
            }

            try {
                setIsSearching(true);
                const results = await wikiPageApi.searchPages(query);
                setSearchResults(results);
            } catch (err) {
                console.error('Error searching pages:', err);
            } finally {
                setIsSearching(false);
            }
        },
        []
    );

    // Debounce search
    useEffect(() => {
        const timer = setTimeout(() => {
            if (searchQuery) {
                handleSearch(searchQuery);
            }
        }, 300);

        return () => clearTimeout(timer);
    }, [searchQuery, handleSearch]);

    const handleCreateRootPage = () => {
        onCreatePage(null, false);
    };

    const handleCreateRootFolder = () => {
        onCreatePage(null, true);
    };

    const handleCreateChildPage = (parentId, isFolder) => {
        onCreatePage(parentId, isFolder);
    };

    return (
        <aside className="page-tree-sidebar" style={style}>
            <div className="sidebar-header">
                <h2>Wiki Pages</h2>
                <div className="sidebar-header-actions">
                    <button
                        className="new-page-btn"
                        onClick={handleCreateRootPage}
                        title="Create new page"
                    >
                        <svg
                            xmlns="http://www.w3.org/2000/svg"
                            width="16"
                            height="16"
                            viewBox="0 0 24 24"
                            fill="none"
                            stroke="currentColor"
                            strokeWidth="2"
                        >
                            <line x1="12" y1="5" x2="12" y2="19" />
                            <line x1="5" y1="12" x2="19" y2="12" />
                        </svg>
                    </button>
                    <button
                        className="new-folder-btn"
                        onClick={handleCreateRootFolder}
                        title="Create new folder"
                    >
                        <FolderIcon size={16} />
                    </button>
                </div>
            </div>

            <div className="search-container">
                <input
                    type="text"
                    className="search-input"
                    placeholder="Search pages..."
                    value={searchQuery}
                    onChange={(e) => setSearchQuery(e.target.value)}
                />
                {searchQuery && (
                    <button
                        className="clear-search-btn"
                        onClick={() => {
                            setSearchQuery('');
                            setSearchResults(null);
                        }}
                    >
                        <svg
                            xmlns="http://www.w3.org/2000/svg"
                            width="14"
                            height="14"
                            viewBox="0 0 24 24"
                            fill="none"
                            stroke="currentColor"
                            strokeWidth="2"
                        >
                            <line x1="18" y1="6" x2="6" y2="18" />
                            <line x1="6" y1="6" x2="18" y2="18" />
                        </svg>
                    </button>
                )}
            </div>

            <div className="tree-container">
                {isLoading ? (
                    <div className="loading-state">Loading pages...</div>
                ) : error ? (
                    <div className="error-state">
                        <span>{error}</span>
                        <button onClick={fetchPageTree}>Retry</button>
                    </div>
                ) : searchResults !== null ? (
                    // Search results view
                    <div className="search-results">
                        {isSearching ? (
                            <div className="loading-state">Searching...</div>
                        ) : searchResults.length === 0 ? (
                            <div className="empty-state">No pages found</div>
                        ) : (
                            searchResults.map((page) => (
                                <div
                                    key={page.id}
                                    className={`search-result-item ${
                                        page.id === selectedPageId ? 'selected' : ''
                                    }`}
                                    onClick={() => onSelectPage(page)}
                                >
                                    <span className="page-icon">
                                        {page.folder ? <FolderIcon /> : <PageIcon />}
                                    </span>
                                    <span className="page-title">{page.title}</span>
                                </div>
                            ))
                        )}
                    </div>
                ) : pages.length === 0 ? (
                    // Empty state
                    <div className="empty-state">
                        <p>No pages yet</p>
                        <button onClick={handleCreateRootPage}>
                            Create your first page
                        </button>
                    </div>
                ) : (
                    // Tree view
                    <div className="page-tree">
                        {pages.map((page) => (
                            <TreeNode
                                key={page.id}
                                page={page}
                                selectedPageId={selectedPageId}
                                onSelectPage={onSelectPage}
                                onCreateChild={handleCreateChildPage}
                            />
                        ))}
                    </div>
                )}
            </div>
        </aside>
    );
};

export default PageTreeSidebar;