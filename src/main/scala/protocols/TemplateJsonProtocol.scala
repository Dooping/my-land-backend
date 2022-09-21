package protocols

import spray.json._


trait TemplateJsonProtocol extends ObjectTypeJsonProtocol with TaskTypeJsonProtocol {
  import actors.Template._

  implicit val objectResponseTypeFormat: RootJsonFormat[ObjectTypeOptionsResponse] = jsonFormat2(ObjectTypeOptionsResponse)
  implicit val objectRegisterTypeFormat: RootJsonFormat[RegisterNewLandObjectTemplate] = jsonFormat3(RegisterNewLandObjectTemplate)
  implicit val objectChangeTypeFormat: RootJsonFormat[ChangeLandObjectTemplate] = jsonFormat3(ChangeLandObjectTemplate)
  implicit val taskResponseTypeFormat: RootJsonFormat[TaskTypeOptionsResponse] = jsonFormat2(TaskTypeOptionsResponse)
  implicit val taskRegisterTypeFormat: RootJsonFormat[RegisterNewTaskTemplate] = jsonFormat3(RegisterNewTaskTemplate)
  implicit val taskChangeTypeFormat: RootJsonFormat[ChangeTaskTemplate] = jsonFormat3(ChangeTaskTemplate)
}
