# My Land - Backend

## Description

Server side of My Land App

## API

### User

| Path                     | Method | Description          |
|--------------------------|--------|----------------------|
| [`/user`](#register)     | POST   | Registers a new user |
| [`/user` ](#login)       | GET    | Logs the user in     |
| [`/user` ](#user-delete) | DELETE | Deletes a user       |

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

#### User Delete
`DELETE /user`

Endpoint to delete the current user.

_Request must have a valid token_

### Authorization
All requests (besides login and register) must send a valid JWT token to the server.
Either in the header `Authorization` as: `Bearer {JWT-token}`.
Or in the parameter `access_token`.

### Land

**Entity:**

| Field       | Type   | Description                                      |
|-------------|--------|--------------------------------------------------|
| id          | number | identifier                                       |
| name        | string | name of the land                                 |
| description | string | description or state of the land                 |
| area        | number | calculation of the area value                    |
| lat         | number | latitude of the visualization point              |
| lon         | number | longitude of the visualization point             |
| zoom        | number | zoom of the camera in the visualization point    |
| bearing     | number | bearing of the camera in the visualization point |
| createdAt   | Date   | timestamp of the type's creation                 |
| modifiedAt  | Date   | timestamp of the type's last change              |

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

**Entity:**

| Field   | Type   | Description                                   |
|---------|--------|-----------------------------------------------|
| id      | number | identifier                                    |
| element | string | GeoJSON string describing the element         |
| status  | string | description of state of the object            |
| typeId  | number | identifier of the [object type](#object-type) |

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

**Entity:**

| Field      | Type   | Description                         |
|------------|--------|-------------------------------------|
| id         | number | identifier                          |
| name       | string | name of the object type             |
| color      | string | hex color of the object             |
| icon       | string | icon representing the object type   |
| createdAt  | Date   | timestamp of the type's creation    |
| modifiedAt | Date   | timestamp of the type's last change |

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

**Entity:**

| Field       | Type   | Description             |
|-------------|--------|-------------------------|
| id          | number | identifier              |
| name        | string | name of the task type   |
| description | string | description of the task |

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

**Entity:**

| Field       | Type      | Description                    |
|-------------|-----------|--------------------------------|
| id          | number    | identifier                     |
| landId      | number    | identifier of the land         |
| objectId    | number    | identifier of the object       |
| taskTypeId  | number    | identifier of the task type    |
| priority    | number    | priority of the task (1 to 5)  |
| notes       | string    | any notes about the task       |
| createdAt   | Date      | timestamp with creation time   |
| modifiedAt  | Date      | timestamp with change time     |
| completedAt | Date/null | timestamp with completion time |
| archivedAt  | Date/null | timestamp with archiving time  |

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

**Object template entity**

| Field     | Type                                          | Description                      |
|-----------|-----------------------------------------------|----------------------------------|
| default   | Map[string, List[[ObjectType](#object-type)]] | default object templates         |
| fromLands | List[List[[ObjectType](#object-type)]]        | object templates in use by lands |

**Task template entity**

| Field     | Type                                      | Description                    |
|-----------|-------------------------------------------|--------------------------------|
| default   | Map[string, List[[TaskType](#task-type)]] | default task templates         |
| fromLands | List[List[[TaskType](#task-type)]]        | task templates in use by lands |

| Path                                          | Method | Parameters       | Permissions | Description                          |
|-----------------------------------------------|--------|------------------|-------------|--------------------------------------|
| [`/template/object`](#get-object-templates)   | GET    | `locale`         | User        | Gets all object templates for locale |
| [`/template/object`](#create-object-template) | POST   |                  | Admin       | Creates a new object template        |
| [`/template/object`](#modify-object-template) | PUT    |                  | Admin       | Edits an existing object template    |
| [`/template/object`](#delete-object-template) | DELETE | `locale`, `name` | Admin       | Deletes an object template           |
| [`/template/task`](#get-task-templates)       | GET    | `locale`         | User        | Gets all task templates for locale   |
| [`/template/task`](#create-task-template)     | POST   |                  | Admin       | Creates a new task template          |
| [`/template/task`](#modify-task-template)     | PUT    |                  | Admin       | Edits an existing task template      |
| [`/template/task`](#delete-task-template)     | DELETE | `locale`, `name` | Admin       | Deletes a task template              |

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

#### Get Task Templates

Fetches all task templates for the provided _locale_

_Request must have a valid token_

#### Create Task Template

Creates a new task template

Requires a payload with the new data
```json
{
  "locale": "en",
  "name": "some name",
  "taskTypes": [
    {
      "name": "example",
      "description": "some description"
    },
    "..."
  ]
}
```

**Only admin users can make this request.**

#### Modify Task Template

Edits the data of a task template.

Requires a payload with the new data
```json
{
  "locale": "en",
  "name": "some name",
  "taskTypes": [
    {
      "name": "example",
      "description": "some description"
    },
    "..."
  ]
}
```
**Only admin users can make this request.**

#### Delete Task Template

Deletes a task template

Requires both _locale_ and _name_ as a query parameters

**Only admin users can make this request.**

