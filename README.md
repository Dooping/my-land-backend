# My Land - Backend

## Description

Server side of My Land App

## API

### User

| Path               | Method | Description          |
|--------------------|--------|----------------------|
| [/user](#register) | POST   | Registers a new user |
| [/user ](#login)   | GET    | Logs the user in     |

#### Register
`POST /user`

Endpoint used to register a new user.
Requires a payload with the user's credentials

```json
{
  "username": "david",
  "password": "password123"
}
```

#### Login
`GET /user`

Endpoint to login the user through basic authentication.

Returns a JWT token in the header `Access-Token`.
The token is valid for 30 days.
