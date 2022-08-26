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

### Authorization
All requests (besides login and register) must send a valid JWT token to the server.
Either in the header `Authorization` as: `Bearer {JWT-token}`.
Or in the parameter `access_token`.

### Land

| Path                                      | Method | Parameters  | Description              |
|-------------------------------------------|--------|-------------|--------------------------|
| [/land](#get-lands)                       | GET    |             | Gets all lands from user |
| [/land](#create-land)                     | POST   |             | Creates a new land       |
| [/land](#modify-land)                     | PATCH  |             | Patches a land           |
| [/land](#get-land)                        | GET    | id={landId} | Fetches a single land    |
| [/land/{landId}](#get-land)               | GET    |             | Fetches a single land    |
| [/land/{landId}](#delete-land)            | DELETE |             | Deletes a land           |
| [/land/{landId}/object](#land-object)     |        |             | Land objects endpoints   |
| [/land/{landId}/objectType](#object-type) |        |             | Object type endpoints    |


#### Get lands

Fetches all the lands from the user

_Request must have a valid token_

#### Create land

Creates a new land

_Request must have a valid token_

Requires a payload with the new land
```json
{
  "name": "example land name",
  "description": "some description",
  "area": 123.45,
  "lat": 12.34,
  "lon": 12.34,
  "zoom": 5,
  "bearing": 123.45, 
  "polygon": "some GeoJSON polygon"
}
```

returns the created land (payload + id)

#### Modify land

Changes either the description or the polygon and values associated with it.

_Request must have a valid token_

Requires one of the following payloads
```json
{
  "id": 1,
  "description": "some description"
}
```
```json
{
  "id": 1,
  "area": 123.45,
  "lat": 12.34,
  "lon": 12.34,
  "zoom": 5,
  "bearing": 123.45, 
  "polygon": "some GeoJSON polygon"
}
```

#### Get land

Fetches a single land

_Request must have a valid token_

The _id_ can be in the path or as a parameter 

#### Delete land

Deletes a single land

_Request must have a valid token_

### Land object

### Object type
