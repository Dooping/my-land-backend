# My Land - Backend

## Description

Server side of My Land App

## API

### User

| Path                 | Method | Description          |
|----------------------|--------|----------------------|
| [`/user`](#register) | POST   | Registers a new user |
| [`/user` ](#login)   | GET    | Logs the user in     |

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

Endpoint to log in the user through basic authentication.

Returns a JWT token in the header `Access-Token`.
The token is valid for 30 days.

### Authorization
All requests (besides login and register) must send a valid JWT token to the server.
Either in the header `Authorization` as: `Bearer {JWT-token}`.
Or in the parameter `access_token`.

### Land

| Path                                        | Method | Parameters    | Description              |
|---------------------------------------------|--------|---------------|--------------------------|
| [`/land`](#get-lands)                       | GET    |               | Gets all lands from user |
| [`/land`](#create-land)                     | POST   |               | Creates a new land       |
| [`/land`](#modify-land)                     | PATCH  |               | Patches a land           |
| [`/land`](#get-land)                        | GET    | `id={landId}` | Fetches a single land    |
| [`/land/{landId}`](#get-land)               | GET    |               | Fetches a single land    |
| [`/land/{landId}`](#delete-land)            | DELETE |               | Deletes a land           |
| [`/land/{landId}/object`](#land-object)     |        |               | Land objects endpoints   |
| [`/land/{landId}/objectType`](#object-type) |        |               | Object type endpoints    |


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

| Path                                                | Method | Parameters    | Description                   |
|-----------------------------------------------------|--------|---------------|-------------------------------|
| [`/land/{landId}/object`](#get-land-objects)        | GET    |               | Gets all objects from land    |
| [`/land/{landId}/object`](#create-land-object)      | POST   |               | Creates a new object          |
| [`/land/{landId}/object`](#delete-objects-by-type)  | DELETE | `type={id}`   | Deletes all objects of a type |
| [`/land/{landId}/object/{id}`](#modify-land-object) | PUT    |               | Edits an object               |
| [`/land/{landId}/object/{id}`](#delete-land-object) | DELETE |               | Deletes an object             |

#### Get Land Objects
Fetches all objects from a land

_Request must have a valid token_

#### Create Land Object

Creates a new object in the given land

_Request must have a valid token_

Requires a payload with the new object
```json
{
  "element": "Some GeoJSON",
  "status": "Some status",
  "typeId": 1
}
```

#### Modify Land Object

Edits the data of a land object

_Request must have a valid token_

Requires a payload with the new data
```json
{
  "element": "Some GeoJSON",
  "status": "Some status",
  "typeId": 1
}
```

#### Delete Land Object

Deletes an existing object

_Request must have a valid token_

Requires an _id_ as a path parameter

#### Delete Objects by Type

Deletes all objects of a given type

_Request must have a valid token_

Requires a _type_ as a query parameter

### Object type

| Path                                                    | Method | Description               |
|---------------------------------------------------------|--------|---------------------------|
| [`/land/{landId}/objectType`](#get-object-types)        | GET    | Gets all object types     |
| [`/land/{landId}/objectType`](#create-object-type)      | POST   | Creates a new object type |
| [`/land/{landId}/objectType/{id}`](#modify-object-type) | PUT    | Edits an object type      |
| [`/land/{landId}/objectType/{id}`](#delete-object-type) | DELETE | Deletes an object type    |

#### Get Object Types

Fetches all object types for a land

_Request must have a valid token_

#### Create Object Type

Creates one or multiple object types in the given land

_Request must have a valid token_

Requires one of the following payloads
```json
{
  "name": "example",
  "color": "#ffffff",
  "icon": "some icon name"
}
```
```json5
[
  {
    "name": "example",
    "color": "#ffffff",
    "icon": "some icon name"
  },
  "..."
]
```

#### Modify Object Type

Edits the data of an object type

_Request must have a valid token_

Requires a payload with the new object type
```json
{
  "name": "example",
  "color": "#ffffff",
  "icon": "some icon name"
}
```

#### Delete Object Type

Deletes an existing object type

_Request must have a valid token_

Requires an _id_ as a path parameter

### Template

| Path                            | Method | Parameters   | Permissions | Description                   |
|---------------------------------|--------|--------------|-------------|-------------------------------|
| [`/template`](#get-templates)   | GET    | locale       | User        | Gets all templates for locale |
| [`/template`](#create-template) | POST   |              | Admin       | Creates a new template        |
| [`/template`](#modify-template) | PUT    |              | Admin       | Edits an existing template    |
| [`/template`](#delete-template) | DELETE | locale, name | Admin       | Deletes a template            |

#### Get Templates

Fetches all templates for the provided _locale_

_Request must have a valid token_

#### Create Template

Creates a new template

Requires a payload with the new data
```json
{
  "locale": "en",
  "name": "some name",
  "objTypes": [
    {
      "name": "example",
      "color": "#ffffff",
      "icon": "some icon name"
    },
    "..."
  ]
}
```

**Only admin users can make this request.**

#### Modify Template

Edits the data of a template.

Requires a payload with the new data
```json
{
  "locale": "en",
  "name": "some name",
  "objTypes": [
    {
      "name": "example",
      "color": "#ffffff",
      "icon": "some icon name"
    },
    "..."
  ]
}
```
**Only admin users can make this request.**

#### Delete Template

Deletes a template

Requires both _locale_ and _name_ as a query parameters

**Only admin users can make this request.**

