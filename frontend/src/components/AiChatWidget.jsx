import { useEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router-dom';
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

/** Pages where an assistant would be out of place: graded quizzes, and an interview in progress. */
const HIDDEN_ON = [/^\/attempts\//, /^\/interview\/[^/]+$/];

/** The assistant reads text, so these are the files that can be attached. */
const ATTACH_TYPES = '.txt,.md,.java,.sql,.js,.jsx,.py,.html,.css,.json,.xml,.yml,.yaml,.c,.cpp,.csv';
const MAX_ATTACHMENT_CHARS = 20000;
const MAX_ATTACHMENT_BYTES = 100 * 1024;
/** How many earlier messages go along with a new question. */
const HISTORY_SENT = 10;
const MAX_SAVED_CHATS = 30;

function loadSessions(storageKey) {
  try {
    const stored = JSON.parse(localStorage.getItem(storageKey));
    return Array.isArray(stored) ? stored : [];
  } catch {
    return [];
  }
}

/** The assistant button and chat window, for signed-in members. Nothing renders (and nothing is asked of the server) when signed out. */
export default function AiChatWidget() {
  const { user } = useAuth();
  const { pathname } = useLocation();
  if (!user || HIDDEN_ON.some((pattern) => pattern.test(pathname))) return null;
  // One panel per member, so signing in as someone else never shows the last person's conversations.
  return <ChatPanel key={user.id} user={user} />;
}

function ChatPanel({ user }) {
  const isAdmin = Boolean(user.admin);
  const storageKey = `nova-chat-${user.id}`;
  const initialGreeting = isAdmin
    ? 'Welcome. I am Nova Admin Co-Pilot. How can I help with platform metrics, course creation or learner analytics?'
    : 'Hello. I am Nova, your AI assistant. How can I help with your courses, technical concepts or learning progress?';

  const quickPrompts = isAdmin ? ADMIN_PROMPTS : LEARNER_PROMPTS;
  const widgetTitle = isAdmin ? 'Nova Admin Co-Pilot' : 'Nova AI Assistant';
  const widgetStatus = isAdmin ? 'Executive platform mode' : 'Connected to the portal';

  const [isOpen, setIsOpen] = useState(false);
  const [showHistory, setShowHistory] = useState(false);
  // Conversations are told apart by this chat's start time and a counter, so starting another never reuses an id.
  const [sessionBase] = useState(() => Date.now().toString());
  const sessionCount = useRef(0);
  const [currentSessionId, setCurrentSessionId] = useState(sessionBase);
  const [messages, setMessages] = useState([{ role: 'assistant', content: initialGreeting }]);
  const [savedSessions, setSavedSessions] = useState(() => loadSessions(storageKey));
  const [input, setInput] = useState('');
  const [attachment, setAttachment] = useState(null);
  const [notice, setNotice] = useState(null);
  const [isListening, setIsListening] = useState(false);
  const [loading, setLoading] = useState(false);

  const messagesEndRef = useRef(null);
  const fileInputRef = useRef(null);
  const recognitionRef = useRef(null);

  // The conversation is kept in this browser, under this member's own key.
  useEffect(() => {
    try {
      localStorage.setItem(storageKey, JSON.stringify(savedSessions));
    } catch { /* the history just won't be remembered */ }
  }, [savedSessions, storageKey]);

  // Keep the current conversation in the saved list as it grows.
  useEffect(() => {
    if (messages.length <= 1) return;
    const firstQuestion = messages.find((m) => m.role === 'user');
    const title = firstQuestion ? firstQuestion.content.slice(0, 36) + (firstQuestion.content.length > 36 ? '...' : '') : 'New conversation';
    const timestamp = new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    setSavedSessions((prev) => {
      const session = { id: currentSessionId, title, timestamp, messages };
      const others = prev.filter((s) => s.id !== currentSessionId);
      const index = prev.findIndex((s) => s.id === currentSessionId);
      const next = index >= 0 ? prev.map((s) => (s.id === currentSessionId ? session : s)) : [session, ...others];
      return next.slice(0, MAX_SAVED_CHATS);
    });
  }, [messages, currentSessionId]);

  useEffect(() => {
    if (isOpen && !showHistory) messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages, isOpen, loading, showHistory]);

  // Voice input uses the browser's own speech recognition.
  useEffect(() => {
    const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!SpeechRecognition) return undefined;
    const rec = new SpeechRecognition();
    rec.continuous = false;
    rec.interimResults = false;
    rec.lang = 'en-US';
    rec.onresult = (event) => {
      const transcript = event.results[0][0].transcript;
      setInput((prev) => (prev ? `${prev} ${transcript}` : transcript));
      setIsListening(false);
    };
    rec.onerror = () => setIsListening(false);
    rec.onend = () => setIsListening(false);
    recognitionRef.current = rec;
    return () => rec.abort?.();
  }, []);

  function toggleVoiceInput() {
    if (!recognitionRef.current) {
      setNotice('Voice input is not supported in this browser. Type your question instead.');
      return;
    }
    if (isListening) {
      recognitionRef.current.stop();
      setIsListening(false);
    } else {
      try {
        recognitionRef.current.start();
        setIsListening(true);
      } catch {
        setIsListening(false);
      }
    }
  }

  function handleFileSelect(e) {
    const file = e.target.files?.[0];
    e.target.value = '';
    if (!file) return;
    setNotice(null);
    if (file.type.startsWith('image/') || file.name.toLowerCase().endsWith('.pdf')) {
      setNotice('The assistant reads text, not images or PDFs. Paste the text or code into your question instead.');
      return;
    }
    if (file.size > MAX_ATTACHMENT_BYTES) {
      setNotice('That file is too large to attach. Choose one under 100 KB.');
      return;
    }
    const reader = new FileReader();
    reader.onload = (event) => {
      const text = String(event.target?.result || '');
      if (text.length > MAX_ATTACHMENT_CHARS) {
        setNotice(`That file is too long. Attach one of up to ${MAX_ATTACHMENT_CHARS.toLocaleString()} characters.`);
        return;
      }
      setAttachment({ name: file.name, text });
    };
    reader.onerror = () => setNotice("We couldn't read that file.");
    reader.readAsText(file);
  }

  function handlePaste(e) {
    const items = e.clipboardData?.items;
    if (items && Array.from(items).some((item) => item.type.startsWith('image/'))) {
      e.preventDefault();
      setNotice('The assistant reads text, not images. Paste the text or code instead.');
    }
  }

  async function handleSend(textToSend) {
    const question = (textToSend || input).trim();
    if ((!question && !attachment) || loading) return;
    setNotice(null);

    const message = question || 'Please review the attached file.';
    const shown = attachment ? `${message}\n[Attached: ${attachment.name}]` : message;
    const asked = [...messages, { role: 'user', content: shown }];
    setMessages(asked);
    if (!textToSend) setInput('');
    const file = attachment;
    setAttachment(null);
    setLoading(true);

    try {
      // Earlier turns only: the greeting and any error replies aren't part of the conversation.
      const history = messages.slice(1).filter((m) => !m.error).slice(-HISTORY_SENT).map((m) => ({ role: m.role, content: m.content.slice(0, 6000) }));
      const response = await api.sendAiChatMessage(message, history, file?.name || null, file?.text || null);
      setMessages((prev) => [...prev, { role: 'assistant', content: response.reply }]);
    } catch (err) {
      setMessages((prev) => [...prev, { role: 'assistant', error: true, content: `I couldn't answer that. ${err.message}` }]);
    } finally {
      setLoading(false);
    }
  }

  function handleKeyDown(e) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  }

  function startNewChat() {
    sessionCount.current += 1;
    setCurrentSessionId(`${sessionBase}-${sessionCount.current}`);
    setMessages([{ role: 'assistant', content: initialGreeting }]);
    setShowHistory(false);
  }

  function loadSession(session) {
    setCurrentSessionId(session.id);
    setMessages(session.messages || [{ role: 'assistant', content: initialGreeting }]);
    setShowHistory(false);
  }

  function deleteSession(e, sessionId) {
    e.stopPropagation();
    setSavedSessions((prev) => prev.filter((s) => s.id !== sessionId));
    if (sessionId === currentSessionId) startNewChat();
  }

  function clearAllHistory() {
    setSavedSessions([]);
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
                  <Icon name="clock" size={16} /> Chat history
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
                <div className={`chat-bubble ${m.role}${m.error ? ' error' : ''}`}>
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

          {notice && <p className="ai-notice" role="status">{notice}</p>}

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
            hidden
            accept={ATTACH_TYPES}
            onChange={handleFileSelect}
          />

          {/* Chat Input Footer */}
          <footer className="ai-chat-input-area">
            <button
              type="button"
              className="ai-attach-btn"
              onClick={() => fileInputRef.current?.click()}
              title="Attach a text or code file"
            >
              <Icon name="paperclip" size={16} />
            </button>

            <button
              type="button"
              className={`ai-mic-btn ${isListening ? 'listening' : ''}`}
              onClick={toggleVoiceInput}
              title={isListening ? "Listening... Click to stop" : "Voice input (uses your browser's speech service)"}
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
              placeholder={isListening ? "Listening..." : "Ask a question..."} maxLength={2000} aria-label="Your question"
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
        aria-label={isOpen ? 'Close the AI assistant' : 'Open the AI assistant'}
        aria-expanded={isOpen}
      >
        {isOpen ? (
          <Icon name="x" size={20} />
        ) : (
          <>
            <Icon name="sparkles" size={18} />
            <span className="ai-widget-label">{isAdmin ? 'Admin Co-Pilot' : 'AI Assistant'}</span>
          </>
        )}
      </button>
    </div>
  );
}
