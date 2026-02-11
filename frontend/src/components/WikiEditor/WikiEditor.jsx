import React, { useEffect, useRef, useState, useCallback } from 'react';
import EditorJS from '@editorjs/editorjs';
import Header from '@editorjs/header';
import List from '@editorjs/list';
import Quote from '@editorjs/quote';
import Code from '@editorjs/code';
import LinkTool from '@editorjs/link';
import Marker from '@editorjs/marker';
import InlineCode from '@editorjs/inline-code';
import ImageTool from '@editorjs/image';
import Table from '@editorjs/table';
import { uploadApi } from '../../services/api';
import './WikiEditor.css';

/**
 * WikiEditor Component
 * Rich text editor with Editor.js, supporting drag-and-drop image uploads
 */
const WIKI_LINK_REGEX = /\[\[([^\]|]+)(?:\|([^\]]*))?\]\]/g;

const WikiEditor = ({
    pageId,
    initialContent,
    onChange,
    onSave,
    onWikiLinkClick,
    readOnly = false,
}) => {
    const editorRef = useRef(null);
    const editorInstance = useRef(null);
    const [isReady, setIsReady] = useState(false);
    const [isDragging, setIsDragging] = useState(false);
    const [uploadProgress, setUploadProgress] = useState(null);

    // Parse initial content if it's a string
    const parseContent = useCallback((content) => {
        if (!content) {
            return { blocks: [] };
        }
        if (typeof content === 'string') {
            try {
                return JSON.parse(content);
            } catch {
                // If it's plain text, wrap in paragraph block
                return {
                    blocks: [
                        {
                            type: 'paragraph',
                            data: { text: content },
                        },
                    ],
                };
            }
        }
        return content;
    }, []);

    // Custom image uploader for Editor.js
    const imageUploader = useCallback(
        (file) => {
            return uploadApi.uploadImage(file, pageId);
        },
        [pageId]
    );

    // Initialize Editor.js (once per mount)
    useEffect(() => {
        if (!editorRef.current || editorInstance.current) return;

        const editor = new EditorJS({
            holder: editorRef.current,
            readOnly,
            placeholder: 'Start writing your wiki page...',
            data: parseContent(initialContent),
            tools: {
                header: {
                    class: Header,
                    config: {
                        levels: [1, 2, 3, 4],
                        defaultLevel: 2,
                    },
                },
                list: {
                    class: List,
                    inlineToolbar: true,
                },
                quote: {
                    class: Quote,
                    inlineToolbar: true,
                    config: {
                        quotePlaceholder: 'Enter a quote',
                        captionPlaceholder: 'Quote author',
                    },
                },
                code: Code,
                linkTool: {
                    class: LinkTool,
                    config: {
                        endpoint: '/api/link-preview',
                    },
                },
                table: {
                    class: Table,
                    inlineToolbar: true,
                    config: {
                        rows: 2,
                        cols: 3,
                    },
                },
                marker: Marker,
                inlineCode: InlineCode,
                image: {
                    class: ImageTool,
                    config: {
                        uploader: {
                            uploadByFile: imageUploader,
                            uploadByUrl: async (url) => {
                                return {
                                    success: 1,
                                    file: { url },
                                };
                            },
                        },
                        captionPlaceholder: 'Image caption',
                    },
                },
            },
            onChange: async () => {
                if (onChange && editorInstance.current) {
                    try {
                        const data = await editorInstance.current.save();
                        onChange(data);
                    } catch (error) {
                        console.error('Error saving editor content:', error);
                    }
                }
            },
            onReady: () => {
                setIsReady(true);
            },
        });

        editorInstance.current = editor;

        return () => {
            if (editorInstance.current && editorInstance.current.destroy) {
                editorInstance.current.destroy();
                editorInstance.current = null;
            }
        };
    }, [pageId]); // eslint-disable-line

    // Toggle readOnly mode without destroying the editor
    useEffect(() => {
        if (!editorInstance.current || !isReady) return;

        const toggle = async () => {
            try {
                const currentMode = await editorInstance.current.readOnly.toggle();
                // If the toggled mode doesn't match desired, toggle again
                if (currentMode !== readOnly) {
                    await editorInstance.current.readOnly.toggle();
                }
            } catch (error) {
                console.error('Error toggling readOnly:', error);
            }
        };

        toggle();
    }, [readOnly, isReady]);

    // Process wiki links in rendered content (read-only mode)
    useEffect(() => {
        if (!isReady || !readOnly || !editorRef.current) return;

        const processWikiLinks = () => {
            const container = editorRef.current;
            const walker = document.createTreeWalker(
                container,
                NodeFilter.SHOW_TEXT,
                null,
                false
            );

            const textNodes = [];
            let node;
            while ((node = walker.nextNode())) {
                if (WIKI_LINK_REGEX.test(node.textContent)) {
                    textNodes.push(node);
                }
                WIKI_LINK_REGEX.lastIndex = 0;
            }

            textNodes.forEach((textNode) => {
                const text = textNode.textContent;
                const fragment = document.createDocumentFragment();
                let lastIndex = 0;
                let match;

                WIKI_LINK_REGEX.lastIndex = 0;
                while ((match = WIKI_LINK_REGEX.exec(text)) !== null) {
                    // Add text before the match
                    if (match.index > lastIndex) {
                        fragment.appendChild(
                            document.createTextNode(text.slice(lastIndex, match.index))
                        );
                    }

                    const pageName = match[1].trim();
                    const displayText = match[2] ? match[2].trim() : pageName;

                    const link = document.createElement('a');
                    link.className = 'wiki-link';
                    link.href = '#';
                    link.textContent = displayText;
                    link.dataset.pageName = pageName;
                    link.addEventListener('click', (e) => {
                        e.preventDefault();
                        if (onWikiLinkClick) {
                            onWikiLinkClick(pageName);
                        }
                    });

                    fragment.appendChild(link);
                    lastIndex = match.index + match[0].length;
                }

                // Add remaining text
                if (lastIndex < text.length) {
                    fragment.appendChild(
                        document.createTextNode(text.slice(lastIndex))
                    );
                }

                textNode.parentNode.replaceChild(fragment, textNode);
            });
        };

        // Small delay to ensure Editor.js has fully rendered
        const timer = setTimeout(processWikiLinks, 100);
        return () => clearTimeout(timer);
    }, [isReady, readOnly, onWikiLinkClick]);

    // Handle drag and drop for images
    const handleDragEnter = useCallback((e) => {
        e.preventDefault();
        e.stopPropagation();
        setIsDragging(true);
    }, []);

    const handleDragLeave = useCallback((e) => {
        e.preventDefault();
        e.stopPropagation();
        setIsDragging(false);
    }, []);

    const handleDragOver = useCallback((e) => {
        e.preventDefault();
        e.stopPropagation();
    }, []);

    const handleDrop = useCallback(
        async (e) => {
            e.preventDefault();
            e.stopPropagation();
            setIsDragging(false);

            const files = Array.from(e.dataTransfer.files);
            const imageFiles = files.filter((file) =>
                file.type.startsWith('image/')
            );

            if (imageFiles.length === 0) {
                return;
            }

            // Upload each image and insert into editor
            for (const file of imageFiles) {
                try {
                    setUploadProgress(0);
                    const result = await uploadApi.uploadFile(
                        file,
                        pageId ? `pages/${pageId}/images` : 'images',
                        (progress) => setUploadProgress(progress)
                    );
                    setUploadProgress(null);

                    // Insert image block into editor
                    if (editorInstance.current) {
                        await editorInstance.current.blocks.insert('image', {
                            file: {
                                url: result.url,
                                objectKey: result.objectKey,
                            },
                            caption: file.name,
                            withBorder: false,
                            stretched: false,
                            withBackground: false,
                        });
                    }
                } catch (error) {
                    console.error('Error uploading image:', error);
                    setUploadProgress(null);
                    alert(`Failed to upload ${file.name}: ${error.message}`);
                }
            }
        },
        [pageId]
    );

    // Save editor content
    const handleSave = useCallback(async () => {
        if (!editorInstance.current || !onSave) return;

        try {
            const data = await editorInstance.current.save();
            await onSave(data);
        } catch (error) {
            console.error('Error saving:', error);
            throw error;
        }
    }, [onSave]);

    // Expose save method via ref
    useEffect(() => {
        if (editorRef.current) {
            editorRef.current.save = handleSave;
        }
    }, [handleSave]);

    return (
        <div className="wiki-editor-container">
            <div
                className={`wiki-editor ${isDragging ? 'dragging' : ''} ${
                    !isReady ? 'loading' : ''
                }`}
                onDragEnter={handleDragEnter}
                onDragLeave={handleDragLeave}
                onDragOver={handleDragOver}
                onDrop={handleDrop}
            >
                {!isReady && (
                    <div className="editor-loading">
                        <span>Loading editor...</span>
                    </div>
                )}
                <div ref={editorRef} className="editor-content" />
                {isDragging && (
                    <div className="drop-overlay">
                        <div className="drop-message">
                            <svg
                                xmlns="http://www.w3.org/2000/svg"
                                width="48"
                                height="48"
                                viewBox="0 0 24 24"
                                fill="none"
                                stroke="currentColor"
                                strokeWidth="2"
                            >
                                <rect
                                    x="3"
                                    y="3"
                                    width="18"
                                    height="18"
                                    rx="2"
                                    ry="2"
                                />
                                <circle cx="8.5" cy="8.5" r="1.5" />
                                <polyline points="21 15 16 10 5 21" />
                            </svg>
                            <span>Drop images here to upload</span>
                        </div>
                    </div>
                )}
                {uploadProgress !== null && (
                    <div className="upload-progress">
                        <div
                            className="progress-bar"
                            style={{ width: `${uploadProgress}%` }}
                        />
                        <span>Uploading... {uploadProgress}%</span>
                    </div>
                )}
            </div>
        </div>
    );
};

export default WikiEditor;