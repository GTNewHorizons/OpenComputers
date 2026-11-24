package li.cil.oc.server.command

import li.cil.oc.Settings
import li.cil.oc.Settings.DebugCardAccess
import li.cil.oc.common.command.SimpleCommand
import net.minecraft.command.{ICommandSender, WrongUsageException}
import net.minecraft.server.MinecraftServer

object DebugWhitelistCommand extends SimpleCommand("oc_debugWhitelist") {
  // Required OP levels:
  //  to revoke your own cards - 0
  //  to do other whitelist manipulation - 2

  override def getRequiredPermissionLevel = 0
  def isOp(sender: ICommandSender) = getOpLevel(sender) >= 2

  override def getCommandUsage(sender: ICommandSender): String =
    if (isOp(sender))
      s"$name [revoke|add|remove|allow|deny|list] <args>\n" +
      s"  add <player> [fn1,fn2,...]  -- add player (must be online); * if no fns given\n" +
      s"  allow <player> fn1,fn2,...  -- grant more functions to existing entry\n" +
      s"  deny <player> fn1,fn2,...   -- revoke specific functions (no-op for wildcard)\n" +
      s"  list [player]               -- list players or a player's allowed functions\n" +
      s"  remove <player>             -- remove player from whitelist\n" +
      s"  revoke [player]             -- invalidate cards without removing player"
    else s"$name revoke"

  /** Looks up an online player and returns (uuid, displayName), or sends an error and returns None. */
  private def resolveOnlinePlayer(sender: ICommandSender, name: String): Option[(String, String)] =
    Option(MinecraftServer.getServer.getConfigurationManager.func_152612_a(name)) match {
      case Some(p) => Some((p.getGameProfile.getId.toString, p.getCommandSenderName))
      case None =>
        sender.addChatMessage(s"§c'$name' is not currently online. The player must be online to be added.")
        None
    }

  override def processCommand(sender: ICommandSender, args: Array[String]): Unit = {
    val wl = Settings.get.debugCardAccess match {
      case w: DebugCardAccess.Whitelist => w
      case _ => throw new WrongUsageException("§cDebug card whitelisting is not enabled.")
    }

    def revokeByName(name: String): Unit =
      wl.uuidForName(name) match {
        case Some(uuid) =>
          wl.invalidate(uuid)
          sender.addChatMessage(s"§aAll debug cards for §e$name§a were invalidated.")
        case None =>
          sender.addChatMessage(s"§c'$name' is not whitelisted.")
      }

    def revokeSelf(): Unit = {
      val name = sender.getCommandSenderName
      val uuid = Option(MinecraftServer.getServer.getConfigurationManager.func_152612_a(name))
        .map(_.getGameProfile.getId.toString)
        .orElse(wl.uuidForName(name))
      uuid match {
        case Some(u) if wl.isWhitelisted(u) =>
          wl.invalidate(u)
          sender.addChatMessage("§aYour debug cards were invalidated.")
        case _ =>
          sender.addChatMessage("§cYou are not whitelisted to use debug card.")
      }
    }

    args match {
      // --- self-service (op level 0) ---
      case Array("revoke") => revokeSelf()

      // --- op-only ---
      case Array("revoke", player) if isOp(sender) => revokeByName(player)

      case Array("list") if isOp(sender) =>
        val players = wl.whitelist.toSeq.sortBy(_._2)
        if (players.nonEmpty)
          sender.addChatMessage("§aWhitelisted: §e" + players.map { case (u, n) => s"$n ($u)" }.mkString(", "))
        else
          sender.addChatMessage("§cNo players are currently whitelisted.")

      case Array("list", name) if isOp(sender) =>
        val uuid = wl.uuidForName(name)
          .orElse(resolveOnlinePlayer(sender, name).map(_._1))
        uuid match {
          case Some(u) =>
            wl.allowedFunctions(u) match {
              case Some(None)               => sender.addChatMessage(s"§a$name§r: §e* (all functions — wildcard)")
              case Some(Some(fns)) if fns.nonEmpty =>
                sender.addChatMessage(s"§a$name§r allowed functions:")
                fns.toSeq.sorted.foreach(fn => sender.addChatMessage(s"  §e$fn"))
              case Some(Some(_))            => sender.addChatMessage(s"§c$name has an empty function set (no calls permitted).")
              case None                     => sender.addChatMessage(s"§c$name is not whitelisted.")
            }
          case None =>
            sender.addChatMessage(s"§c'$name' not found in whitelist and is not online.")
        }

      case Array("add", name) if isOp(sender) =>
        resolveOnlinePlayer(sender, name) match {
          case Some((uuid, displayName)) =>
            wl.add(uuid, displayName)
            sender.addChatMessage(s"§aAdded §e$displayName§a ($uuid) with wildcard (*) access.")
          case None => // error already printed by resolveOnlinePlayer
        }

      case Array("add", name, fnsArg) if isOp(sender) =>
        resolveOnlinePlayer(sender, name) match {
          case Some((uuid, displayName)) =>
            val fns = fnsArg.split(",").map(_.trim).filter(_.nonEmpty).toSet
            wl.add(uuid, displayName, fns)
            sender.addChatMessage(s"§aAdded §e$displayName§a ($uuid) with functions: §e${fns.toSeq.sorted.mkString(", ")}")
          case None => // error already printed by resolveOnlinePlayer
        }

      case Array("allow", name, fnsArg) if isOp(sender) =>
        wl.uuidForName(name) match {
          case Some(uuid) =>
            val fns = fnsArg.split(",").map(_.trim).filter(_.nonEmpty).toSet
            wl.allow(uuid, fns)
            sender.addChatMessage(s"§aGranted to §e$name§a: §e${fns.toSeq.sorted.mkString(", ")}")
          case None =>
            sender.addChatMessage(s"§c'$name' is not in the whitelist. Use 'add' first.")
        }

      case Array("deny", name, fnsArg) if isOp(sender) =>
        wl.uuidForName(name) match {
          case Some(uuid) =>
            val fns = fnsArg.split(",").map(_.trim).filter(_.nonEmpty).toSet
            if (!wl.deny(uuid, fns))
              sender.addChatMessage(s"§c'$name' has wildcard (*) access — cannot deny specific functions. Remove and re-add with an explicit list.")
            else
              sender.addChatMessage(s"§aRevoked from §e$name§a: §e${fns.toSeq.sorted.mkString(", ")}")
          case None =>
            sender.addChatMessage(s"§c'$name' is not in the whitelist.")
        }

      case Array("remove", name) if isOp(sender) =>
        wl.uuidForName(name) match {
          case Some(uuid) =>
            wl.remove(uuid)
            sender.addChatMessage(s"§aRemoved §e$name§a from the whitelist.")
          case None =>
            sender.addChatMessage(s"§c'$name' is not in the whitelist.")
        }

      case _ =>
        sender.addChatMessage("§e" + getCommandUsage(sender))
    }
  }
}
