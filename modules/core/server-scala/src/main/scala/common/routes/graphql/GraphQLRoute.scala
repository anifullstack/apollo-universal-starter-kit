package common.routes.graphql

import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.model.Multipart.FormData
import akka.http.scaladsl.model.headers.`Set-Cookie`
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import common.graphql.UserContext
import common.graphql.schema.GraphQL
import common.routes.graphql.jsonProtocols.GraphQLMessage
import common.routes.graphql.jsonProtocols.GraphQLMessageJsonProtocol._
import modules.session.JWTSessionImpl
import sangria.renderer.SchemaRenderer
import spray.json._

import scala.concurrent.ExecutionContext
import scala.util.Try

class GraphQLRoute(
    httpHandler: HttpHandler,
    session: JWTSessionImpl,
    webSocketHandler: WebSocketHandler,
    graphQL: GraphQL
)(implicit val executionContext: ExecutionContext, actorMaterializer: ActorMaterializer) {

  val routes: Route =
    path("graphql") {
      extractRequest {
        request =>
          get {
            handleWebSocketMessagesForProtocol(webSocketHandler.handleMessages, GraphQLMessage.graphQlWebsocketProtocol)
          } ~
            post {
              session.withOptional {
                maybeSession =>
                  withHeaders(UserContext(requestHeaders = request.headers.toList, session = maybeSession)) {
                    userCtx =>
                      entity(as[GraphQLMessage]) {
                        graphQlMessage =>
                          onComplete(httpHandler.handleQuery(graphQlMessage, userCtx)) {
                            response: Try[ToResponseMarshallable] =>
                              session.withChanges(maybeSession, userCtx.session) {
                                complete(response)
                              }
                          }
                      } ~
                        entity(as[Seq[GraphQLMessage]]) {
                          graphQlMessages =>
                            onComplete(httpHandler.handleBatchQuery(graphQlMessages, userCtx)) {
                              response: Try[ToResponseMarshallable] =>
                                session.withChanges(maybeSession, userCtx.session) {
                                  complete(response)
                                }
                            }
                        } ~
                        entity(as[Multipart.FormData]) {
                          formData =>
                            formFields('operations, 'map) {
                              (graphQLMessage, files) =>
                                //for each file, the key is the file multipart form field name and the value is an array of operations paths
                                val filesMap = files.asJson.convertTo[Map[String, List[String]]]
                                val formDataParts: Source[FormData.BodyPart, Any] =
                                  formData.parts.filter(part => filesMap.keySet.contains(part.name))
                                onComplete(
                                  httpHandler.handleQuery(
                                    graphQLMessage.asJson.convertTo[GraphQLMessage],
                                    userCtx.copy(filesData = formDataParts)
                                  )
                                ) {
                                  response: Try[ToResponseMarshallable] =>
                                    session.withChanges(maybeSession, userCtx.session) {
                                      complete(response)
                                    }
                                }
                            }
                        }
                  }
              }
            }
      }
    } ~
      (path("graphql" / "schema") & get) {
        complete(SchemaRenderer.renderSchema(graphQL.schema))
      } ~
      (path("graphiql") & get) {
        getFromResource("web/graphiql.html")
      }

  private def withHeaders(userCtx: UserContext)(ctxToRoute: UserContext => Route) =
    mapResponseHeaders(
      _ ++
        userCtx.newHeaders.toList ++
        userCtx.newCookies.toList.map(`Set-Cookie`(_))
    )(ctxToRoute(userCtx))
}
