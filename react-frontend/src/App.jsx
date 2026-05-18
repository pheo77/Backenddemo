import React, { useState } from 'react';
import { Mail, Lock, ArrowRight, Github, Chrome } from 'lucide-react';
import './index.css';

function App() {
  const [formData, setFormData] = useState({
    email: '',
    password: '',
    remember: false
  });

  const handleChange = (e) => {
    const { name, value, type, checked } = e.target;
    setFormData(prev => ({
      ...prev,
      [name]: type === 'checkbox' ? checked : value
    }));
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    console.log('Login Attempt:', formData);
    // Add authentication logic here connecting to Spring Boot Backend
  };

  return (
    <div className="login-container">
      <div className="login-card">
        
        <div className="brand-header">
          <div style={{ backgroundColor: 'var(--primary-color)', padding: '0.5rem', borderRadius: '0.5rem' }}>
            <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="white" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M12 2L2 7l10 5 10-5-10-5zM2 17l10 5 10-5M2 12l10 5 10-5"/>
            </svg>
          </div>
          <h1 className="brand-title">Smart<span>City</span></h1>
        </div>

        <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
          <h2 style={{ fontSize: '1.25rem', fontWeight: '600', marginBottom: '0.5rem' }}>Welcome Back</h2>
          <p className="text-muted" style={{ fontSize: '0.875rem' }}>Enter your credentials to access your account</p>
        </div>

        <form onSubmit={handleSubmit}>
          <div className="form-group">
            <label htmlFor="email" className="form-label">Email Address</label>
            <div className="input-wrapper">
              <Mail className="input-icon" size={18} />
              <input
                type="email"
                id="email"
                name="email"
                className="form-input"
                placeholder="citizen@smartcity.gov"
                value={formData.email}
                onChange={handleChange}
                required
              />
            </div>
          </div>

          <div className="form-group">
            <label htmlFor="password" className="form-label">Password</label>
            <div className="input-wrapper">
              <Lock className="input-icon" size={18} />
              <input
                type="password"
                id="password"
                name="password"
                className="form-input"
                placeholder="••••••••"
                value={formData.password}
                onChange={handleChange}
                required
              />
            </div>
          </div>

          <div className="form-options">
            <label className="checkbox-wrapper">
              <input
                type="checkbox"
                name="remember"
                className="form-checkbox"
                checked={formData.remember}
                onChange={handleChange}
              />
              <span>Remember me</span>
            </label>
            <a href="#" className="forgot-link">Forgot password?</a>
          </div>

          <button type="submit" className="btn-primary">
            Sign In <ArrowRight size={18} />
          </button>
        </form>

        <div className="divider">Or continue with</div>

        <div className="social-login">
          <button type="button" className="btn-social">
            <Chrome size={18} /> Google
          </button>
          <button type="button" className="btn-social">
            <Github size={18} /> GitHub
          </button>
        </div>

        <div className="signup-prompt">
          Don't have an account? <a href="#">Create an account</a>
        </div>
      </div>
    </div>
  );
}

export default App;
