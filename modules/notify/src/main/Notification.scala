package lila.notify
import alleycats.Zero
import ornicar.scalalib.ThreadLocalRandom
import reactivemongo.api.bson.Macros.Annotations.Key

import lila.common.licon
import lila.common.paginator.Paginator
import lila.notify.Notification.*

sealed abstract class NotificationContent(val key: String)

case class MentionedInThread(
    mentionedBy: UserId,
    topicName: String,
    topidId: ForumTopicId,
    category: ForumCategId,
    postId: ForumPostId
) extends NotificationContent("mention")

case class StreamStart(
    streamerId: UserId,
    streamerName: String
) extends NotificationContent("streamStart")

case class PrivateMessage(user: UserId, text: String) extends NotificationContent("privateMessage")

case class InvitedToStudy(
    invitedBy: UserId,
    studyName: StudyName,
    studyId: StudyId
) extends NotificationContent("invitedStudy")

case class TeamJoined(
    id: TeamId,
    name: String
) extends NotificationContent("teamJoined")

case class TitledTournamentInvitation(
    id: TourId,
    text: String
) extends NotificationContent("titledTourney")

case class GameEnd(
    gameId: GameFullId,
    opponentId: Option[UserId],
    win: Option[Win]
) extends NotificationContent("gameEnd")

case object ReportedBanned extends NotificationContent("reportedBanned")

case class RatingRefund(perf: String, points: Int) extends NotificationContent("ratingRefund")

case object CoachReview extends NotificationContent("coachReview")

case class PlanStart(userId: UserId)  extends NotificationContent("planStart")  // BC
case class PlanExpire(userId: UserId) extends NotificationContent("planExpire") // BC

case class CorresAlarm(
    gameId: GameId,
    opponent: String
) extends NotificationContent("corresAlarm")

case class IrwinDone(
    userId: UserId
) extends NotificationContent("irwinDone")

case class KaladinDone(
    userId: UserId
) extends NotificationContent("kaladinDone")

case class GenericLink(
    url: String,
    title: Option[String],
    text: Option[String],
    icon: licon.Icon
) extends NotificationContent("genericLink")

case class BroadcastRound(
    url: String,
    title: String,
    text: String
) extends NotificationContent("broadcastRound")

case class PushNotification(
    to: Iterable[NotifyAllows],
    content: NotificationContent,
    params: Iterable[(String, String)] = Nil
)

private[notify] case class Notification(
    @Key("_id") id: Notification.Id,
    notifies: UserId,
    content: NotificationContent,
    read: NotificationRead,
    createdAt: Instant,
    expiresAt: Option[Instant] = None
):
  def to = notifies

object Notification:

  opaque type Id = String
  object Id extends OpaqueString[Id]

  opaque type UnreadCount = Int
  object UnreadCount extends OpaqueInt[UnreadCount]:
    given Zero[UnreadCount] = Zero(0)

  opaque type NotificationRead = Boolean
  object NotificationRead extends YesNo[NotificationRead]

  case class AndUnread(pager: Paginator[Notification], unread: UnreadCount)

  def make[U: UserIdOf](
      to: U,
      content: NotificationContent,
      expiresIn: Option[FiniteDuration] = none
  ): Notification =
    Notification(
      id = ThreadLocalRandom.nextString(8),
      notifies = to.id,
      content = content,
      read = NotificationRead(false),
      createdAt = nowInstant,
      expiresAt = expiresIn.map(nowInstant.plus(_))
    )
