package li.cil.oc.server.command

import li.cil.oc.Settings
import li.cil.oc.Settings.DebugCardAccess
import li.cil.oc.common.command.SimpleCommand
import net.minecraft.command.{ICommandSender, WrongUsageException}

object DebugWhitelistCommand extends SimpleCommand("oc_debugWhitelist") {
  // Required OP levels:
  //  to revoke your own cards - 0
  //  to do other whitelist manipulation - 2

  override def getRequiredPermissionLevel = 0
  def isOp(sender: ICommandSender) = getOpLevel(sender) >= 2

  override def getCommandUsage(sender: ICommandSender): String =
    if (isOp(sender))
      s"$name [revoke|add|remove|allow|deny|list] <args>\n" +
      s"  add <player> [fn1,fn2,...]  -- add player; * access if no fns given\n" +
      s"  allow <player> fn1,fn2,...  -- grant more functions to existing entry\n" +
      s"  deny <player> fn1,fn2,...   -- revoke specific functions (no-op for wildcard)\n" +
      s"  list [player]               -- list players or a player's allowed functions\n" +
      s"  remove <player>             -- remove player from whitelist\n" +
      s"  revoke [player]             -- invalidate cards without removing player"
    else s"$name revoke"

  override def processCommand(sender: ICommandSender, args: Array[String]): Unit = {
    val wl = Settings.get.debugCardAccess match {
      case w: DebugCardAccess.Whitelist => w
      case _ => throw new WrongUsageException("§cDebug card whitelisting is not enabled.")
    }

    def revokeUser(player: String): Unit = {
      if (wl.isWhitelisted(player)) {
        wl.invalidate(player)
        sender.addChatMessage("§aAll debug cards for that player were invalidated.")
      } else sender.addChatMessage("§cPlayer is not whitelisted to use debug card.")
    }

    args match {
      // --- self-service (op level 0) ---
      case Array("revoke") => revokeUser(sender.getCommandSenderName)

      // --- op-only ---
      case Array("revoke", player) if isOp(sender) => revokeUser(player)

      case Array("list") if isOp(sender) =>
        val players = wl.whitelist
        if (players.nonEmpty)
          sender.addChatMessage("§aCurrently whitelisted players: §e" + players.toSeq.sorted.mkString(", "))
        else
          sender.addChatMessage("§cNo players are currently whitelisted.")

      case Array("list", player) if isOp(sender) =>
        wl.allowedFunctions(player) match {
          case Some(None) =>
            sender.addChatMessage(s"§a$player§r: §e* (all functions — wildcard)")
          case Some(Some(fns)) if fns.nonEmpty =>
            sender.addChatMessage(s"§a$player§r allowed functions:")
            fns.toSeq.sorted.foreach(fn => sender.addChatMessage(s"  §e$fn"))
          case Some(Some(_)) =>
            sender.addChatMessage(s"§c$player is whitelisted but has an empty function set (no calls permitted).")
          case None =>
            sender.addChatMessage(s"§c$player is not whitelisted.")
        }

      case Array("add", player) if isOp(sender) =>
        wl.add(player)
        sender.addChatMessage(s"§aPlayer §e$player§a added to whitelist with wildcard (*) access.")

      case Array("add", player, fnsArg) if isOp(sender) =>
        val fns = fnsArg.split(",").map(_.trim).filter(_.nonEmpty).toSet
        wl.add(player, fns)
        sender.addChatMessage(s"§aPlayer §e$player§a added with functions: §e${fns.toSeq.sorted.mkString(", ")}")

      case Array("allow", player, fnsArg) if isOp(sender) =>
        if (!wl.isWhitelisted(player)) {
          sender.addChatMessage(s"§c$player is not whitelisted. Use 'add' first.")
        } else {
          val fns = fnsArg.split(",").map(_.trim).filter(_.nonEmpty).toSet
          wl.allow(player, fns)
          sender.addChatMessage(s"§aGranted to §e$player§a: §e${fns.toSeq.sorted.mkString(", ")}")
        }

      case Array("deny", player, fnsArg) if isOp(sender) =>
        if (!wl.isWhitelisted(player)) {
          sender.addChatMessage(s"§c$player is not whitelisted.")
        } else {
          val fns = fnsArg.split(",").map(_.trim).filter(_.nonEmpty).toSet
          if (!wl.deny(player, fns)) {
            sender.addChatMessage(s"§c$player has wildcard (*) access — cannot deny specific functions. " +
              "Remove and re-add with an explicit function list.")
          } else {
            sender.addChatMessage(s"§aRevoked from §e$player§a: §e${fns.toSeq.sorted.mkString(", ")}")
          }
        }

      case Array("remove", player) if isOp(sender) =>
        wl.remove(player)
        sender.addChatMessage(s"§aPlayer §e$player§a was removed from the whitelist.")

      case _ =>
        sender.addChatMessage("§e" + getCommandUsage(sender))
    }
  }
}
