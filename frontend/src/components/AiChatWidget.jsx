import { useEffect, useRef, useState } from 'react';
import { api } from '../api';
import { useAuth } from '../context/AuthContext';
import Icon from './Icon';

function renderMarkdown(text) {
  if (!text) return null;

  const parts = text.split(/(```[\s\S]*?```)/g);

  return parts.map((part, index) => {
    if (part.startsWith('```') && part.endsWith('```')) {
      const firstLineEnd = part.indexOf('\n');
      const language = firstLineEnd > 3 ? part.substring(3, firstLineEnd).trim() : '';
      const codeContent = firstLineEnd > 0 ? part.substring(firstLineEnd + 1, part.length - 3) : part.substring(3, part.length - 3);

      return (
        <div key={index} className="chat-code-block">
          {language && <div className="chat-code-lang">{language}</div>}
          <pre>
            <code>{codeContent.trim()}</code>
          </pre>
        </div>
      );
    }

    const lines = part.split('\n');
    const formatted = lines.map((line, lIdx) => {
      let trimmed = line.trim();
      if (!trimmed) return <div key={lIdx} className="chat-spacer" />;

      if (trimmed.startsWith('### ')) {
        return <h4 key={lIdx} className="chat-h3">{parseInline(trimmed.substring(4))}</h4>;
      }
      if (trimmed.startsWith('## ') || trimmed.startsWith('# ')) {
        return <h3 key={lIdx} className="chat-h2">{parseInline(trimmed.replace(/^#+\s*/, ''))}</h3>;
      }
      if (trimmed.startsWith('* ') || trimmed.startsWith('- ') || trimmed.startsWith('• ')) {
        return (
          <div key={lIdx} className="chat-bullet">
            <span className="chat-dot">•</span>
            <span>{parseInline(trimmed.substring(2))}</span>
          </div>
        );
      }

      return <p key={lIdx} className="chat-para">{parseInline(line)}</p>;
    });

    return <div key={index}>{formatted}</div>;
  });
}

function parseInline(text) {
  const tokens = text.split(/(\*\*.*?\*\*|`.*?`)/g);
  return tokens.map((token, i) => {
    if (token.startsWith('**') && token.endsWith('**')) {
      return <strong key={i}>{token.slice(2, -2)}</strong>;
    }
    if (token.startsWith('`') && token.endsWith('`')) {
      return <code key={i} className="chat-inline-code">{token.slice(1, -1)}</code>;
    }
    return token;
  });
}

const LEARNER_PROMPTS = [
  'How can I learn Java in 30 days?',
  'Explain RAG (Retrieval-Augmented Generation)',
  'What are popular web technologies?',
  'Show my current learning streak and badges',
];

const ADMIN_PROMPTS = [
  'Show platform enrollment metrics & student count',
  'Help me create a new course outline',
  'Summarize platform hackathons and active users',
];

export default function AiChatWidget() {
  const { user } = useAuth();

  // Hide AI Chatbot for guests on public landing page/login
  if (!user) {
    return null;
  }

  const isAdmin = Boolean(user?.admin || user?.role === 'ADMIN' || user?.isAdmin);
  const initialGreeting = isAdmin
    ? "Welcome Admin. I am Nova Admin Co-Pilot. How can I assist you with platform metrics, course creation, or user analytics?"
    : "Hello. I am Nova, your AI Assistant. How can I assist you with your courses, technical concepts (Java/SQL), or learning progress?";

  const quickPrompts = isAdmin ? ADMIN_PROMPTS : LEARNER_PROMPTS;
  const widgetTitle = isAdmin ? "Nova Admin Co-Pilot" : "Nova AI Assistant";
  const widgetStatus = isAdmin ? "Executive Platform Mode" : "Connected to Portal";

  const [isOpen, setIsOpen] = useState(false);
  const [showHistory, setShowHistory] = useState(false);
  const [currentSessionId, setCurrentSessionId] = useState(() => Date.now().toString());
  const [messages, setMessages] = useState([
    {
      role: 'assistant',
      content: initialGreeting,
    },
  ]);
  const [savedSessions, setSavedSessions] = useState([]);
  
  const [input, setInput] = useState('');
  const [attachment, setAttachment] = useState(null);
  const [isListening, setIsListening] = useState(false);
  const [loading, setLoading] = useState(false);
  
  const messagesEndRef = useRef(null);
  const fileInputRef = useRef(null);
  const recognitionRef = useRef(null);

  // Load chat history sessions from localStorage
  useEffect(() => {
    try {
      const storageKey = isAdmin ? 'nova_admin_sessions' : 'nova_learner_sessions';
      const stored = localStorage.getItem(storageKey);
      if (stored) {
        setSavedSessions(JSON.parse(stored));
      }
    } catch (e) {
      console.warn('Failed to parse chat history', e);
    }
  }, [isAdmin]);

  // Save current conversation session to localStorage whenever messages update
  useEffect(() => {
    if (messages.length <= 1) return;

    const firstUserMsg = messages.find((m) => m.role === 'user');
    const title = firstUserMsg ? firstUserMsg.content.slice(0, 36) + (firstUserMsg.content.length > 36 ? '...' : '') : 'New Conversation';
    const timestamp = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });

    setSavedSessions((prev) => {
      const existingIdx = prev.findIndex((s) => s.id === currentSessionId);
      const sessionObj = {
        id: currentSessionId,
        title,
        timestamp,
        messages,
      };

      let updated;
      if (existingIdx >= 0) {
        updated = [...prev];
        updated[existingIdx] = sessionObj;
      } else {
        updated = [sessionObj, ...prev];
      }

      try {
        const storageKey = isAdmin ? 'nova_admin_sessions' : 'nova_learner_sessions';
        localStorage.setItem(storageKey, JSON.stringify(updated));
      } catch (e) {
        console.warn('Failed to save chat history', e);
      }
      return updated;
    });
  }, [messages, currentSessionId, isAdmin]);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    if (isOpen && !showHistory) {
      scrollToBottom();
    }
  }, [messages, isOpen, loading, showHistory]);

  // Voice Input Setup (Speech-to-Text)
  useEffect(() => {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (SpeechRecognition) {
      const rec = new SpeechRecognition();
      rec.continuous = false;
      rec.interimResults = false;
      rec.lang = 'en-US';

      rec.onresult = (event) => {
        const transcript = event.results[0][0].transcript;
        setInput((prev) => (prev ? prev + ' ' + transcript : transcript));
        setIsListening(false);
      };

      rec.onerror = () => {
        setIsListening(false);
      };

      rec.onend = () => {
        setIsListening(false);
      };

      recognitionRef.current = rec;
    }
  }, []);

  function toggleVoiceInput() {
    if (!recognitionRef.current) {
      alert('Voice recognition is not supported in this browser. Please type your query.');
      return;
    }
    if (isListening) {
      recognitionRef.current.stop();
      setIsListening(false);
    } else {
      try {
        recognitionRef.current.start();
        setIsListening(true);
      } catch (err) {
        setIsListening(false);
      }
    }
  }

  function handleFileSelect(e) {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (event) => {
      setAttachment({
        name: file.name,
        type: file.type,
        data: event.target?.result,
      });
    };
    if (file.type.startsWith('image/')) {
      reader.readAsDataURL(file);
    } else {
      reader.readAsText(file);
    }
  }

  async function handleSend(textToSend) {
    const query = (textToSend || input).trim();
    if ((!query && !attachment) || loading) return;

    let userContent = query;
    if (attachment) {
      userContent += `\n[Attached: ${attachment.name}]`;
    }

    const newMessages = [...messages, { role: 'user', content: userContent }];
    setMessages(newMessages);
    if (!textToSend) setInput('');
    const currentAttachment = attachment;
    setAttachment(null);
    setLoading(true);

    try {
      const historyPayload = newMessages.slice(1).map((m) => ({ role: m.role, content: m.content }));
      const payload = {
        message: query,
        history: historyPayload,
        attachmentName: currentAttachment?.name || null,
        attachmentData: currentAttachment?.data || null,
      };

      const response = await api.sendAiChatMessage(payload.message, payload.history, payload.attachmentName, payload.attachmentData);
      setMessages((prev) => [...prev, { role: 'assistant', content: response.reply }]);
    } catch (err) {
      setMessages((prev) => [
        ...prev,
        {
          role: 'assistant',
          content: 'Unable to connect to the AI service. Please try again.',
        },
      ]);
    } finally {
      setLoading(false);
    }
  }

  function handlePaste(e) {
    const items = e.clipboardData?.items;
    if (!items) return;

    for (let i = 0; i < items.length; i++) {
      if (items[i].type.indexOf('image') !== -1) {
        const file = items[i].getAsFile();
        if (file) {
          const reader = new FileReader();
          reader.onload = (event) => {
            setAttachment({
              name: `Pasted_Screenshot_${Date.now()}.png`,
              type: file.type,
              data: event.target?.result,
            });
          };
          reader.readAsDataURL(file);
          e.preventDefault();
          break;
        }
      }
    }
  }

  function handleKeyDown(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  }

  function startNewChat() {
    setCurrentSessionId(Date.now().toString());
    setMessages([
      {
        role: 'assistant',
        content: initialGreeting,
      },
    ]);
    setShowHistory(false);
  }

  function loadSession(session) {
    setCurrentSessionId(session.id);
    setMessages(session.messages || [{ role: 'assistant', content: initialGreeting }]);
    setShowHistory(false);
  }

  function deleteSession(e, sessionId) {
    e.stopPropagation();
    const updated = savedSessions.filter((s) => s.id !== sessionId);
    setSavedSessions(updated);
    try {
      const storageKey = isAdmin ? 'nova_admin_sessions' : 'nova_learner_sessions';
      localStorage.setItem(storageKey, JSON.stringify(updated));
    } catch (err) {
      console.warn('Failed to update storage after delete', err);
    }

    if (sessionId === currentSessionId) {
      startNewChat();
    }
  }

  function clearAllHistory() {
    setSavedSessions([]);
    const storageKey = isAdmin ? 'nova_admin_sessions' : 'nova_learner_sessions';
    localStorage.removeItem(storageKey);
    startNewChat();
  }

  return (
    <div className="ai-widget-wrapper">
      {/* Floating Chat Panel */}
      {isOpen && (
        <div className="ai-chat-window">
          {/* Header */}
          <header className="ai-chat-header">
            <div className="ai-chat-avatar-title">
              <div className="ai-avatar-badge">
                <Icon name="sparkles" size={16} />
              </div>
              <div>
                <h3>{widgetTitle}</h3>
                <span className="ai-status">{widgetStatus}</span>
              </div>
            </div>
            <div className="ai-header-actions">
              <button
                type="button"
                className={`ai-icon-btn ${showHistory ? 'active' : ''}`}
                onClick={() => setShowHistory((v) => !v)}
                title="Chat history"
              >
                <Icon name="clock" size={15} />
              </button>

              <button type="button" className="ai-icon-btn" onClick={startNewChat} title="New conversation">
                <Icon name="plus" size={15} />
              </button>

              <button type="button" className="ai-icon-btn" onClick={clearAllHistory} title="Clear all history">
                <Icon name="trash-2" size={15} />
              </button>

              <button type="button" className="ai-icon-btn" onClick={() => setIsOpen(false)} title="Close chat">
                <Icon name="x" size={16} />
              </button>
            </div>
          </header>

          {/* ChatGPT-Style History Overlay Panel */}
          {showHistory && (
            <div className="ai-history-panel">
              <div className="ai-history-header">
                <h4>
                  <Icon name="clock" size={16} /> Chat History ({isAdmin ? 'Admin' : 'Learner'})
                </h4>
                <button type="button" className="ai-new-chat-btn" onClick={startNewChat}>
                  <Icon name="plus" size={14} /> New Chat
                </button>
              </div>

              <div className="ai-history-list">
                {savedSessions.length === 0 ? (
                  <div className="ai-history-empty">No previous conversations yet.</div>
                ) : (
                  savedSessions.map((s) => (
                    <div
                      key={s.id}
                      className={`ai-history-item ${s.id === currentSessionId ? 'active' : ''}`}
                      onClick={() => loadSession(s)}
                    >
                      <div className="ai-history-info">
                        <span className="ai-history-title">{s.title}</span>
                        <span className="ai-history-time">{s.timestamp || 'Recent'}</span>
                      </div>
                      <button
                        type="button"
                        className="ai-history-del-btn"
                        onClick={(e) => deleteSession(e, s.id)}
                        title="Delete thread"
                      >
                        <Icon name="trash-2" size={14} />
                      </button>
                    </div>
                  ))
                )}
              </div>
            </div>
          )}

          {/* Messages Body */}
          <div className="ai-chat-messages">
            {messages.map((m, idx) => (
              <div key={idx} className={`chat-bubble-row ${m.role}`}>
                <div className={`chat-bubble ${m.role}`}>
                  {renderMarkdown(m.content)}
                </div>
              </div>
            ))}

            {loading && (
              <div className="chat-bubble-row assistant">
                <div className="chat-bubble assistant loading">
                  <span className="dot-pulse" />
                  <span className="dot-pulse" />
                  <span className="dot-pulse" />
                </div>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          {/* Quick Prompts */}
          {messages.length <= 2 && !loading && (
            <div className="ai-quick-prompts">
              {quickPrompts.map((p, i) => (
                <button key={i} type="button" className="quick-chip" onClick={() => handleSend(p)}>
                  {p}
                </button>
              ))}
            </div>
          )}

          {/* Attachment Preview Banner */}
          {attachment && (
            <div className="ai-attachment-preview">
              <span>Attached: {attachment.name}</span>
              <button type="button" className="ai-attach-remove" onClick={() => setAttachment(null)}>
                <Icon name="x" size={14} />
              </button>
            </div>
          )}

          {/* Hidden File Input */}
          <input
            type="file"
            ref={fileInputRef}
            style={{ display: 'none' }}
            accept="image/*,.txt,.java,.sql,.js,.py,.html,.css,.json,.md,.pdf"
            onChange={handleFileSelect}
          />

          {/* Chat Input Footer */}
          <footer className="ai-chat-input-area">
            <button
              type="button"
              className="ai-attach-btn"
              onClick={() => fileInputRef.current?.click()}
              title="Attach screenshot, image, code, or document"
            >
              <Icon name="paperclip" size={16} />
            </button>

            <button
              type="button"
              className={`ai-mic-btn ${isListening ? 'listening' : ''}`}
              onClick={toggleVoiceInput}
              title={isListening ? "Listening... Click to stop" : "Voice input"}
            >
              <Icon name="mic" size={16} />
            </button>

            <input
              type="text"
              className="ai-chat-input"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              onPaste={handlePaste}
              placeholder={isListening ? "Listening..." : "Ask anything or paste screenshot (Ctrl+V)..."}
            />

            <button
              type="button"
              className="ai-send-btn"
              onClick={() => handleSend()}
              title="Send query"
            >
              <Icon name="arrow-up" size={16} strokeWidth={3} />
            </button>
          </footer>
        </div>
      )}

      {/* Floating Toggle Button (Bottom-Right) */}
      <button
        type="button"
        className={`ai-widget-toggle ${isOpen ? 'active' : ''}`}
        onClick={() => setIsOpen((v) => !v)}
        aria-label="Open AI Assistant"
        title="Open AI Assistant"
      >
        {isOpen ? (
          <Icon name="x" size={20} />
        ) : (
          <>
            <Icon name="sparkles" size={18} />
            <span className="ai-widget-label">{isAdmin ? "Admin Co-Pilot" : "AI Assistant"}</span>
          </>
        )}
      </button>
    </div>
  );
}
