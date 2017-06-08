package lila.game

import chess.format.Forsyth
import chess.format.pgn.{Pgn, Tag, Parser, ParsedPgn}
import chess.format.{pgn => chessPgn}
import chess.{Centis, Color, White, Black}
import lichess.Users

object PgnDump {

  def apply(game: Game, users: Users, initialFen: Option[String]): Pgn = {
    val ts = tags(game, users, initialFen)
    val fenSituation = ts find (_.name == Tag.FEN) flatMap { case Tag(_, fen) => Forsyth <<< fen }
    val moves2 =
      if (fenSituation.fold(false)(_.situation.color.black)) ".." :: game.pgnMoves
      else game.pgnMoves
    val turns = makeTurns(
      moves2,
      fenSituation.map(_.fullMoveNumber) getOrElse 1,
      game.bothClockStates getOrElse Vector.empty,
      game.startColor)
    Pgn(ts, turns)
  }

  def result(game: Game) =
    if (game.finished) game.winnerColor.fold("1/2-1/2")(_.fold("1-0", "0-1"))
    else "*"

  private def gameUrl(id: String) = s"https://lichess.org/$id"

  private def elo(p: Player) = p.rating.fold("?")(_.toString)

  private def player(g: Game, color: Color, users: Users) = {
    val player = g.player(color)
    player.aiLevel.fold(users(color).name)("lichess AI level " + _)
  }

  private val customStartPosition: Set[chess.variant.Variant] =
    Set(chess.variant.Chess960, chess.variant.FromPosition, chess.variant.Horde, chess.variant.RacingKings)

  private def eventOf(game: Game) = {
    val perf = game.perfType.fold("Standard")(_.name)
    game.tournamentId.map { id =>
      s"${game.mode} $perf tournament https://lichess.org/tournament/$id"
    } orElse game.simulId.map { id =>
      s"$perf simul https://lichess.org/simul/$id"
    } getOrElse {
      s"${game.mode} $perf game"
    }
  }

  def tags(game: Game, users: Users, initialFen: Option[String]): List[Tag] = List(
    Tag(_.Event, eventOf(game)),
    Tag(_.Site, gameUrl(game.id)),
    Tag(_.White, player(game, White, users)),
    Tag(_.Black, player(game, Black, users)),
    Tag(_.Result, result(game)),
    Tag(_.UTCDate, Tag.UTCDate.format.print(game.createdAt)),
    Tag(_.UTCTime, Tag.UTCTime.format.print(game.createdAt)),
    Tag(_.WhiteElo, elo(game.whitePlayer)),
    Tag(_.BlackElo, elo(game.blackPlayer))) ::: List(
      game.whitePlayer.ratingDiff.map { rd => Tag(_.WhiteRatingDiff, rd) },
      game.blackPlayer.ratingDiff.map { rd => Tag(_.BlackRatingDiff, rd) },
      users.white.title.map { t => Tag(_.WhiteTitle, t) },
      users.black.title.map { t => Tag(_.BlackTitle, t) },
      Some(Tag(_.ECO, game.opening.fold("?")(_.opening.eco))),
      Some(Tag(_.Opening, game.opening.fold("?")(_.opening.name))),
      Some(Tag(_.TimeControl, game.clock.fold("-") { c => s"${c.limit.roundSeconds}+${c.increment.roundSeconds}" })),
      Some(Tag(_.Termination, {
        import chess.Status._
        game.status match {
          case Created | Started => "Unterminated"
          case Aborted | NoStart => "Abandoned"
          case Timeout | Outoftime => "Time forfeit"
          case Resign | Draw | Stalemate | Mate | VariantEnd => "Normal"
          case Cheat => "Rules infraction"
          case UnknownFinish => "Unknown"
        }
      })),
      if (customStartPosition(game.variant)) Some(Tag(_.FEN, initialFen getOrElse "?")) else None,
      if (customStartPosition(game.variant)) Some(Tag("SetUp", "1")) else None,
      if (game.variant.exotic) Some(Tag(_.Variant, game.variant.name.capitalize)) else None
    ).flatten

  private def makeTurns(moves: List[String], from: Int, clocks: Vector[Centis], startColor: Color): List[chessPgn.Turn] =
    (moves grouped 2).zipWithIndex.toList map {
      case (moves, index) =>
        val clockOffset = startColor.fold(0, 1)
        chessPgn.Turn(
          number = index + from,
          white = moves.headOption filter (".." !=) map { san =>
          chessPgn.Move(
            san = san,
            secondsLeft = clocks lift (index * 2 - clockOffset) map (_.roundSeconds))
        },
          black = moves lift 1 map { san =>
          chessPgn.Move(
            san = san,
            secondsLeft = clocks lift (index * 2 + 1 - clockOffset) map (_.roundSeconds))
        })
    } filterNot (_.isEmpty)
}
