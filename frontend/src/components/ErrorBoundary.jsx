import { Component } from 'react';

/** Without this, one rendering mistake blanks the whole page. Shows a way out instead, and logs the cause. */
export default class ErrorBoundary extends Component {
  state = { failed: false };

  static getDerivedStateFromError() {
    return { failed: true };
  }

  componentDidCatch(error, info) {
    console.error('The page crashed while rendering:', error, info?.componentStack);
  }

  render() {
    if (!this.state.failed) return this.props.children;
    return (
      <div className="container-wide" style={{ textAlign: 'center', paddingTop: '18vh' }}>
        <h1>Something went wrong</h1>
        <p className="field-hint">This page hit an unexpected problem. Reloading usually fixes it.</p>
        <p>
          <button type="button" className="btn btn-primary" onClick={() => window.location.reload()}>Reload the page</button>{' '}
          <a className="btn" href="/courses">Go to courses</a>
        </p>
      </div>
    );
  }
}
