# Backend Security Implementation Guide

This document provides a comprehensive overview of the security setup implemented in the Backtesting Engine backend. It is designed to help frontend developers correctly integrate authentication and authorization.

## Authentication Architecture

The application uses **Stateless JWT (JSON Web Token) Authentication**. 
- The server does not maintain user sessions.
- Authentication relies entirely on the presence of a valid JWT token in the request headers.
- Passwords are encrypted in the database using **BCrypt**.

## API Endpoints

### Public Endpoints (No Token Required)
The following endpoints are whitelisted and accessible to anyone:
- `POST /api/auth/register`
- `POST /api/auth/login`
- preflight `OPTIONS` requests
- `/error` (standard Spring MVC error page handling)

### Secured Endpoints
**ALL other endpoints** in the application require a valid JWT token. 

## Payload Contracts

### 1. Registration (`POST /api/auth/register`)

Registers a new user and automatically logs them in, returning a JWT token.

**Request Body:**
```json
{
  "username": "testuser",
  "password": "securePassword123"
}
```

**Success Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI... (full jwt token)"
}
```

**Error Responses:**
- `400 Bad Request`: If the username already exists.

---

### 2. Login (`POST /api/auth/login`)

Authenticates an existing user and returns a JWT token.

**Request Body:**
```json
{
  "username": "testuser",
  "password": "securePassword123"
}
```

**Success Response (200 OK):**
```json
{
  "token": "eyJhbGciOiJIUzI... (full jwt token)"
}
```

**Error Responses:**
- `403 Forbidden` / `401 Unauthorized`: Bad credentials.

---

## Frontend Integration Guide

To successfully interact with secured endpoints, the frontend must implement the following flow:

### 1. Store the Token
Upon a successful `/login` or `/register`, extract the `token` from the response and store it securely. We recommend using `localStorage` or `sessionStorage` for frontend-heavy SPA applications.

```javascript
// Example storing token
const response = await fetch('/api/auth/login', { ... });
const data = await response.json();
localStorage.setItem('authToken', data.token);
```

### 2. Attach Token to API Requests
For any request to a secured endpoint (e.g., fetching backtest results or searching symbols), the frontend must attach the token in the `Authorization` header using the **Bearer** schema.

```javascript
// Example secured request
const token = localStorage.getItem('authToken');

fetch('/api/v1/some-secured-endpoint', {
    method: 'GET', // or POST, etc.
    headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}` 
    }
});
```

> [!WARNING]
> Do NOT forget the space between `Bearer` and the token. The backend explicitly checks for the `Bearer ` prefix.

### 3. Handle Token Expirations (403 Errors)
Tokens currently expire after 24 hours. If a token is expired or invalid, the backend will return a `403 Forbidden` status code. The frontend should intercept `403` responses globally and redirect the user back to the login page.

```javascript
// Example using axios interceptors
axios.interceptors.response.use(response => response, error => {
    if (error.response.status === 403) {
        localStorage.removeItem('authToken');
        window.location.href = '/login'; // Redirect to login
    }
    return Promise.reject(error);
});
```

## CORS Configuration

Cross-Origin Resource Sharing (CORS) is configured natively inside Spring Security. If the frontend is hosted on the allowed origins, requests will not be blocked by the browser.

Currently Allowed Origins:
- `http://localhost:5173` (Vite development server)
- `http://localhost:3000` (Next.js / CRA development server)
- `https://backtest-livid.vercel.app` (Production environment)

**Configuration Details:**
- **Allowed Methods**: `GET`, `POST`, `PUT`, `DELETE`, `OPTIONS`, `HEAD`, `PATCH`
- **Allowed Headers**: `*`
- **Credentials Allowed**: `true`

> [!NOTE]
> If you deploy the frontend to a new domain, you must update the list of allowed origins in `com.rods.backtestingstrategies.security.SecurityConfig` or requests will fail with CORS issues.
