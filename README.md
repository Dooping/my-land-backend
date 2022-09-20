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
| [`/land/{landId}/taskType`](#task-type)     |        |               | Task type endpoints      |
| [`/land/{landId}/task`](#task)              |        |               | Task endpoints           |


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

### Land Object

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

| Path                                                    | Method | Description                |
|---------------------------------------------------------|--------|----------------------------|
| [`/land/{landId}/objectType`](#get-object-types)        | GET    | Gets all object types      |
| [`/land/{landId}/objectType`](#create-object-type)      | POST   | Creates new object type(s) |
| [`/land/{landId}/objectType/{id}`](#modify-object-type) | PUT    | Edits an object type       |
| [`/land/{landId}/objectType/{id}`](#delete-object-type) | DELETE | Deletes an object type     |

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

Requires an _id_ as a path parameter

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

### Task Type

| Path                                                | Method | Description                   |
|-----------------------------------------------------|--------|-------------------------------|
| [`/land/{landId}/taskType`](#get-task-types)        | GET    | Gets all task types from land |
| [`/land/{landId}/taskType`](#create-task-type)      | POST   | Creates new task type(s)      |
| [`/land/{landId}/taskType/{id}`](#modify-task-type) | PUT    | Edits a task type             |
| [`/land/{landId}/taskType/{id}`](#delete-task-type) | DELETE | Deletes a task type           |

#### Get Task Types

Fetches all task types for a land

_Request must have a valid token_

#### Create Task Type

Creates one or multiple task types in the given land

_Request must have a valid token_

Requires one of the following payloads
```json
{
  "name": "Planting",
  "description": "planting seeds for some crops"
}
```
```json
[
  {
    "name": "Planting",
    "description": "planting seeds for some crops"
  },
  "..."
]
```

#### Modify Task Type

Edits the data of a task type

_Request must have a valid token_

Requires an _id_ as a path parameter

Requires a payload with the new task type
```json
{
  "name": "Planting",
  "description": "planting seeds for some crops"
}
```

#### Delete Task Type

Deletes an existing task type

_Request must have a valid token_

Requires an _id_ as a path parameter

### Task

| Path                                     | Method | Parameters                         | Description                |
|------------------------------------------|--------|------------------------------------|----------------------------|
| [`/task/{id}`](#get-tasks)               | GET    | (optional) `query=all\season\open` | Gets all user tasks        |
| [`/land/{landId}/task`](#get-land-tasks) | GET    | (optional) `objectId={id}`         | Gets all land/object tasks |
| [`/land/{landId}/task`](#create-task)    | POST   |                                    | Creates new task(s)        |
| [`/task/{id}`](#modify-task)             | PUT    |                                    | Edits a task               |
| [`/task/{id}/complete`](#complete-task)  | PUT    |                                    | Completes a task           |
| [`/task/{id}/archive`](#archive-task)    | PUT    |                                    | Archives a task            |
| [`/task/{id}`](#delete-task)             | DELETE |                                    | Deletes a task             |

#### Get tasks

Fetches user tasks

The default value for the _query_ parameter is `all`.

The possible values for the _query_ parameter are:
- `all`:  fetches all user tasks, in any state
- `season`: fetches only tasks that are not archived
- `open`: fetches only tasks that are neither completed nor archived

_Request must have a valid token_

#### Get land tasks

Fetches all tasks from the land.
If the parameter _objectId_ is set, only fetches tasks for the specified _object_

_Request must have a valid token_

#### Create task

Creates one or multiple tasks in the given land

_Request must have a valid token_

Requires one of the following payloads
```json
{
  "objectId": 1,
  "taskTypeId": 2,
  "priority": 3,
  "notes": "some notes about the task"
}
```
```json
[
  {
    "objectId": 1,
    "taskTypeId": 2,
    "priority": 3,
    "notes": "some notes about the task"
  },
  "..."
]
```

#### Modify task

Edits the data of a task

_Request must have a valid token_

Requires an _id_ as a path parameter

Requires a payload with the new task type
```json
{
  "objectId": 1,
  "taskTypeId": 2,
  "priority": 3,
  "notes": "some notes about the task"
}
```

#### Complete task

Sets a task as completed

_Request must have a valid token_

Requires an _id_ as a path parameter

#### Archive task

Sets a task as archived

_Request must have a valid token_

Requires an _id_ as a path parameter

#### Delete task

Deletes a task

_Request must have a valid token_

Requires an _id_ as a path parameter

### Template

| Path                                          | Method | Parameters       | Permissions | Description                   |
|-----------------------------------------------|--------|------------------|-------------|-------------------------------|
| [`/template/object`](#get-object-templates)   | GET    | `locale`         | User        | Gets all templates for locale |
| [`/template/object`](#create-object-template) | POST   |                  | Admin       | Creates a new template        |
| [`/template/object`](#modify-object-template) | PUT    |                  | Admin       | Edits an existing template    |
| [`/template/object`](#delete-object-template) | DELETE | `locale`, `name` | Admin       | Deletes a template            |

#### Get Object Templates

Fetches all object templates for the provided _locale_

_Request must have a valid token_

#### Create Object Template

Creates a new object template

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

#### Modify Object Template

Edits the data of an object template.

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

#### Delete Object Template

Deletes an object template

Requires both _locale_ and _name_ as a query parameters

**Only admin users can make this request.**

