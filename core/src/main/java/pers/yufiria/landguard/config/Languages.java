package pers.yufiria.landguard.config;

import crypticlib.lang.LangHandler;
import crypticlib.lang.entry.StringLangEntry;

@LangHandler(langFileFolder = "lang")
public class Languages {

    public static final StringLangEntry PREFIX = new StringLangEntry("prefix");
    public static final StringLangEntry UNSUPPORTED_VERSION = new StringLangEntry("unsupported_version");
    public static final StringLangEntry LOAD_FINISH = new StringLangEntry("load_finish");

    // ================= 所有者类型显示 =================
    public static final StringLangEntry OWNER_TYPE_PLAYER = new StringLangEntry("owner_type.player");
    public static final StringLangEntry OWNER_TYPE_GROUP = new StringLangEntry("owner_type.group");
    public static final StringLangEntry OWNER_TYPE_SERVER = new StringLangEntry("owner_type.server");

    public static final StringLangEntry COMMAND_NO_PERM = new StringLangEntry("command.no_perm");
    public static final StringLangEntry COMMAND_PLAYER_ONLY = new StringLangEntry("command.player_only");
    public static final StringLangEntry COMMAND_VERSION = new StringLangEntry("command.version");
    public static final StringLangEntry COMMAND_RELOAD_RELOADING = new StringLangEntry("command.reload.reloading");
    public static final StringLangEntry COMMAND_RELOAD_SUCCESS = new StringLangEntry("command.reload.success");
    public static final StringLangEntry COMMAND_RELOAD_EXCEPTION = new StringLangEntry("command.reload.exception");

    public static final StringLangEntry COMMAND_CLAIM_SUCCESS = new StringLangEntry("command.claim.success");
    public static final StringLangEntry COMMAND_CLAIM_USAGE = new StringLangEntry("command.claim.usage");
    public static final StringLangEntry COMMAND_CLAIM_RADIUS_INVALID = new StringLangEntry("command.claim.radius_invalid");
    public static final StringLangEntry COMMAND_CLAIM_RADIUS_TOO_LARGE = new StringLangEntry("command.claim.radius_too_large");
    public static final StringLangEntry COMMAND_CLAIM_AUTO_ON = new StringLangEntry("command.claim.auto_on");
    public static final StringLangEntry COMMAND_CLAIM_AUTO_OFF = new StringLangEntry("command.claim.auto_off");
    public static final StringLangEntry COMMAND_CLAIM_SKIPPED = new StringLangEntry("command.claim.skipped");
    public static final StringLangEntry COMMAND_BOUNDARY_ON = new StringLangEntry("command.boundary.on");
    public static final StringLangEntry COMMAND_BOUNDARY_OFF = new StringLangEntry("command.boundary.off");
    public static final StringLangEntry COMMAND_UNCLAIM_SUCCESS = new StringLangEntry("command.unclaim.success");
    public static final StringLangEntry COMMAND_UNCLAIM_USAGE = new StringLangEntry("command.unclaim.usage");
    public static final StringLangEntry COMMAND_UNCLAIM_AUTO_ON = new StringLangEntry("command.unclaim.auto_on");
    public static final StringLangEntry COMMAND_UNCLAIM_AUTO_OFF = new StringLangEntry("command.unclaim.auto_off");
    public static final StringLangEntry COMMAND_LIST_HEADER = new StringLangEntry("command.list.header");
    public static final StringLangEntry COMMAND_LIST_ENTRY = new StringLangEntry("command.list.entry");
    public static final StringLangEntry COMMAND_LIST_EMPTY = new StringLangEntry("command.list.empty");
    public static final StringLangEntry COMMAND_INFO_UNCLAIMED = new StringLangEntry("command.info.unclaimed");
    public static final StringLangEntry COMMAND_INFO_HEADER = new StringLangEntry("command.info.header");
    public static final StringLangEntry COMMAND_INFO_NAME = new StringLangEntry("command.info.name");
    public static final StringLangEntry COMMAND_INFO_OWNER = new StringLangEntry("command.info.owner");
    public static final StringLangEntry COMMAND_INFO_WORLD = new StringLangEntry("command.info.world");
    public static final StringLangEntry COMMAND_INFO_CHUNKS = new StringLangEntry("command.info.chunks");
    public static final StringLangEntry COMMAND_INFO_CREATED = new StringLangEntry("command.info.created");
    public static final StringLangEntry COMMAND_FAIL_OVERLAP = new StringLangEntry("command.fail.overlap");
    public static final StringLangEntry COMMAND_FAIL_NOT_ADJACENT = new StringLangEntry("command.fail.not_adjacent");
    public static final StringLangEntry COMMAND_FAIL_QUOTA_EXCEEDED = new StringLangEntry("command.fail.quota_exceeded");
    public static final StringLangEntry COMMAND_FAIL_INVALID_TARGETS = new StringLangEntry("command.fail.invalid_targets");
    public static final StringLangEntry COMMAND_FAIL_NOT_CLAIMED = new StringLangEntry("command.fail.not_claimed");
    public static final StringLangEntry COMMAND_FAIL_NOT_OWNER = new StringLangEntry("command.fail.not_owner");
    public static final StringLangEntry COMMAND_FAIL_INVALID_NAME = new StringLangEntry("command.fail.invalid_name");
    public static final StringLangEntry COMMAND_FAIL_ALREADY_OWNED = new StringLangEntry("command.fail.already_owned");
    public static final StringLangEntry COMMAND_FAIL_TARGET_HAS_CLAIM = new StringLangEntry("command.fail.target_has_claim");

    // ================= 领地改名 / 转让 =================
    public static final StringLangEntry COMMAND_RENAME_USAGE = new StringLangEntry("command.rename.usage");
    public static final StringLangEntry COMMAND_RENAME_SUCCESS = new StringLangEntry("command.rename.success");
    public static final StringLangEntry COMMAND_TRANSFER_USAGE = new StringLangEntry("command.transfer.usage");
    public static final StringLangEntry COMMAND_TRANSFER_SUCCESS = new StringLangEntry("command.transfer.success");
    public static final StringLangEntry COMMAND_TRANSFER_FAIL_PLAYER_NOT_FOUND = new StringLangEntry("command.transfer.fail.player_not_found");
    public static final StringLangEntry COMMAND_TRANSFER_FAIL_GROUP_NOT_FOUND = new StringLangEntry("command.transfer.fail.group_not_found");
    public static final StringLangEntry COMMAND_TRANSFER_GROUP_SUCCESS = new StringLangEntry("command.transfer.group_success");

    public static final StringLangEntry PROTECTION_DENIED = new StringLangEntry("protection.denied");

    // ================= 进入/离开领地提示 =================
    public static final StringLangEntry CLAIM_ENTER_NOTIFY = new StringLangEntry("claim.enter.notify");
    public static final StringLangEntry CLAIM_ENTER_ACTIONBAR = new StringLangEntry("claim.enter.actionbar");
    public static final StringLangEntry CLAIM_EXIT_NOTIFY = new StringLangEntry("claim.exit.notify");
    public static final StringLangEntry CLAIM_EXIT_ACTIONBAR = new StringLangEntry("claim.exit.actionbar");

    // ================= Hook 提示（控制台） =================
    public static final StringLangEntry HOOK_VAULT_MISSING = new StringLangEntry("hook.vault.missing");
    public static final StringLangEntry HOOK_VAULT_NO_PROVIDER = new StringLangEntry("hook.vault.no_provider");
    public static final StringLangEntry HOOK_VAULT_HOOKED = new StringLangEntry("hook.vault.hooked");
    public static final StringLangEntry HOOK_VAULT_API_MISSING = new StringLangEntry("hook.vault.api_missing");
    public static final StringLangEntry HOOK_VAULT_ADAPTER_FAILED = new StringLangEntry("hook.vault.adapter_failed");

    // ================= 用户组 =================
    public static final StringLangEntry COMMAND_GROUP_USAGE = new StringLangEntry("command.group.usage");
    public static final StringLangEntry COMMAND_GROUP_CREATE_SUCCESS = new StringLangEntry("command.group.create.success");
    public static final StringLangEntry COMMAND_GROUP_DISBAND_SUCCESS = new StringLangEntry("command.group.disband.success");
    public static final StringLangEntry COMMAND_GROUP_INVITE_SENT = new StringLangEntry("command.group.invite.sent");
    public static final StringLangEntry COMMAND_GROUP_INVITE_RECEIVED = new StringLangEntry("command.group.invite.received");
    public static final StringLangEntry COMMAND_GROUP_ACCEPT_SUCCESS = new StringLangEntry("command.group.accept.success");
    public static final StringLangEntry COMMAND_GROUP_DENY_SUCCESS = new StringLangEntry("command.group.deny.success");
    public static final StringLangEntry COMMAND_GROUP_LEAVE_SUCCESS = new StringLangEntry("command.group.leave.success");
    public static final StringLangEntry COMMAND_GROUP_KICK_SUCCESS = new StringLangEntry("command.group.kick.success");
    public static final StringLangEntry COMMAND_GROUP_TRANSFER_SUCCESS = new StringLangEntry("command.group.transfer.success");
    public static final StringLangEntry COMMAND_GROUP_RENAME_USAGE = new StringLangEntry("command.group.rename.usage");
    public static final StringLangEntry COMMAND_GROUP_RENAME_SUCCESS = new StringLangEntry("command.group.rename.success");
    public static final StringLangEntry COMMAND_GROUP_ROLE_CREATED = new StringLangEntry("command.group.role.created");
    public static final StringLangEntry COMMAND_GROUP_ROLE_ASSIGNED = new StringLangEntry("command.group.role.assigned");
    public static final StringLangEntry COMMAND_GROUP_LIST_HEADER = new StringLangEntry("command.group.list.header");
    public static final StringLangEntry COMMAND_GROUP_LIST_ENTRY = new StringLangEntry("command.group.list.entry");
    public static final StringLangEntry COMMAND_GROUP_LIST_EMPTY = new StringLangEntry("command.group.list.empty");
    public static final StringLangEntry COMMAND_GROUP_INFO_HEADER = new StringLangEntry("command.group.info.header");
    public static final StringLangEntry COMMAND_GROUP_INFO_NAME = new StringLangEntry("command.group.info.name");
    public static final StringLangEntry COMMAND_GROUP_INFO_LEADER = new StringLangEntry("command.group.info.leader");
    public static final StringLangEntry COMMAND_GROUP_INFO_MEMBERS = new StringLangEntry("command.group.info.members");
    public static final StringLangEntry COMMAND_GROUP_INFO_ROLES = new StringLangEntry("command.group.info.roles");
    public static final StringLangEntry COMMAND_GROUP_FAIL_KEY_TAKEN = new StringLangEntry("command.group.fail.key_taken");
    public static final StringLangEntry COMMAND_GROUP_FAIL_INVALID_KEY = new StringLangEntry("command.group.fail.invalid_key");
    public static final StringLangEntry COMMAND_GROUP_FAIL_INVALID_NAME = new StringLangEntry("command.group.fail.invalid_name");
    public static final StringLangEntry COMMAND_GROUP_FAIL_NOT_FOUND = new StringLangEntry("command.group.fail.not_found");
    public static final StringLangEntry COMMAND_GROUP_FAIL_NOT_LEADER = new StringLangEntry("command.group.fail.not_leader");
    public static final StringLangEntry COMMAND_GROUP_FAIL_NOT_MANAGER = new StringLangEntry("command.group.fail.not_manager");
    public static final StringLangEntry COMMAND_GROUP_FAIL_NOT_MEMBER = new StringLangEntry("command.group.fail.not_member");
    public static final StringLangEntry COMMAND_GROUP_FAIL_TARGET_NOT_MEMBER = new StringLangEntry("command.group.fail.target_not_member");
    public static final StringLangEntry COMMAND_GROUP_FAIL_ALREADY_MEMBER = new StringLangEntry("command.group.fail.already_member");
    public static final StringLangEntry COMMAND_GROUP_FAIL_NO_INVITE = new StringLangEntry("command.group.fail.no_invite");
    public static final StringLangEntry COMMAND_GROUP_FAIL_LEADER_CANNOT_LEAVE = new StringLangEntry("command.group.fail.leader_cannot_leave");
    public static final StringLangEntry COMMAND_GROUP_FAIL_CANNOT_KICK = new StringLangEntry("command.group.fail.cannot_kick");
    public static final StringLangEntry COMMAND_GROUP_FAIL_ROLE_EXISTS = new StringLangEntry("command.group.fail.role_exists");
    public static final StringLangEntry COMMAND_GROUP_FAIL_ROLE_NOT_FOUND = new StringLangEntry("command.group.fail.role_not_found");
    public static final StringLangEntry COMMAND_GROUP_FAIL_ROLE_ID_INVALID = new StringLangEntry("command.group.fail.role_id_invalid");
    public static final StringLangEntry COMMAND_GROUP_FAIL_ROLE_BUILTIN = new StringLangEntry("command.group.fail.role_builtin");
    public static final StringLangEntry COMMAND_GROUP_FAIL_CLAIM_NOT_FOUND = new StringLangEntry("command.group.fail.claim_not_found");
    public static final StringLangEntry COMMAND_GROUP_FAIL_NOT_CLAIM_OWNER = new StringLangEntry("command.group.fail.not_claim_owner");
    public static final StringLangEntry COMMAND_GROUP_FAIL_TARGET_NOT_FOUND = new StringLangEntry("command.group.fail.target_not_found");

    // ================= 经济 =================
    public static final StringLangEntry COMMAND_ECONOMY_UNAVAILABLE = new StringLangEntry("command.economy.unavailable");
    public static final StringLangEntry COMMAND_ECONOMY_USAGE = new StringLangEntry("command.economy.usage");
    public static final StringLangEntry COMMAND_ECONOMY_INVALID_AMOUNT = new StringLangEntry("command.economy.invalid_amount");
    public static final StringLangEntry COMMAND_ECONOMY_INSUFFICIENT_FUNDS = new StringLangEntry("command.economy.insufficient_funds");
    public static final StringLangEntry COMMAND_ECONOMY_QUOTA_IN_USE = new StringLangEntry("command.economy.quota_in_use");
    public static final StringLangEntry COMMAND_ECONOMY_NOTHING_TO_SELL = new StringLangEntry("command.economy.nothing_to_sell");
    public static final StringLangEntry COMMAND_BUY_SUCCESS = new StringLangEntry("command.buy.success");
    public static final StringLangEntry COMMAND_SELL_SUCCESS = new StringLangEntry("command.sell.success");
    public static final StringLangEntry COMMAND_BANK_USAGE = new StringLangEntry("command.bank.usage");
    public static final StringLangEntry COMMAND_BANK_BALANCE = new StringLangEntry("command.bank.balance");
    public static final StringLangEntry COMMAND_BANK_DEPOSIT_SUCCESS = new StringLangEntry("command.bank.deposit.success");
    public static final StringLangEntry COMMAND_BANK_WITHDRAW_SUCCESS = new StringLangEntry("command.bank.withdraw.success");
    public static final StringLangEntry COMMAND_BANK_UNCLAIMED = new StringLangEntry("command.bank.unclaimed");
    public static final StringLangEntry COMMAND_BANK_FORBIDDEN = new StringLangEntry("command.bank.forbidden");
    public static final StringLangEntry COMMAND_BANK_EMPTY = new StringLangEntry("command.bank.empty");

    // ================= Upkeep / 回收 =================
    public static final StringLangEntry UPKEEP_DEBT_WARNING = new StringLangEntry("upkeep.debt.warning");
    public static final StringLangEntry UPKEEP_DEBT_RELEASED = new StringLangEntry("upkeep.debt.released");
    public static final StringLangEntry UPKEEP_INACTIVITY_WARNING = new StringLangEntry("upkeep.inactivity.warning");
    public static final StringLangEntry UPKEEP_INACTIVITY_RELEASED = new StringLangEntry("upkeep.inactivity.released");
    public static final StringLangEntry UPKEEP_ORPHAN_WARNING = new StringLangEntry("upkeep.orphan.warning");
    public static final StringLangEntry UPKEEP_ORPHAN_RELEASED = new StringLangEntry("upkeep.orphan.released");

    // ================= 管理命令 =================
    public static final StringLangEntry COMMAND_ADMIN_USAGE = new StringLangEntry("command.admin.usage");
    public static final StringLangEntry COMMAND_ADMIN_CLAIM_SUCCESS = new StringLangEntry("command.admin.claim.success");
    public static final StringLangEntry COMMAND_ADMIN_UNCLAIM_SUCCESS = new StringLangEntry("command.admin.unclaim.success");
    public static final StringLangEntry COMMAND_ADMIN_RELEASE_SUCCESS = new StringLangEntry("command.admin.release.success");
    public static final StringLangEntry COMMAND_ADMIN_TRANSFER_SUCCESS = new StringLangEntry("command.admin.transfer.success");
    public static final StringLangEntry COMMAND_ADMIN_EXEMPT_SET = new StringLangEntry("command.admin.exempt.set");
    public static final StringLangEntry COMMAND_ADMIN_RENAME_SUCCESS = new StringLangEntry("command.admin.rename.success");
    public static final StringLangEntry COMMAND_ADMIN_RUN_DONE = new StringLangEntry("command.admin.run.done");
    public static final StringLangEntry COMMAND_ADMIN_INFO_ADMIN = new StringLangEntry("command.admin.info.admin");
    public static final StringLangEntry COMMAND_ADMIN_INFO_EXEMPT = new StringLangEntry("command.admin.info.exempt");
    public static final StringLangEntry COMMAND_ADMIN_INFO_ORPHAN = new StringLangEntry("command.admin.info.orphan");
    public static final StringLangEntry COMMAND_ADMIN_ORPHANS_HEADER = new StringLangEntry("command.admin.orphans.header");
    public static final StringLangEntry COMMAND_ADMIN_ORPHANS_ENTRY = new StringLangEntry("command.admin.orphans.entry");
    public static final StringLangEntry COMMAND_ADMIN_ORPHANS_EMPTY = new StringLangEntry("command.admin.orphans.empty");
    public static final StringLangEntry COMMAND_ADMIN_FAIL_CLAIM_NOT_FOUND = new StringLangEntry("command.admin.fail.claim_not_found");
    public static final StringLangEntry COMMAND_ADMIN_FAIL_INVALID_PLAYER = new StringLangEntry("command.admin.fail.invalid_player");
    public static final StringLangEntry COMMAND_ADMIN_FAIL_ALREADY_OWNED = new StringLangEntry("command.admin.fail.already_owned");
    public static final StringLangEntry COMMAND_ADMIN_FAIL_INVALID_ARGUMENT = new StringLangEntry("command.admin.fail.invalid_argument");

    // ================= GUI（Task 8） =================
    public static final StringLangEntry MENU_COMMON_BACK = new StringLangEntry("menu.common.back");
    public static final StringLangEntry MENU_COMMON_PREV = new StringLangEntry("menu.common.prev_page");
    public static final StringLangEntry MENU_COMMON_NEXT = new StringLangEntry("menu.common.next_page");

    public static final StringLangEntry MENU_LIST_TITLE = new StringLangEntry("menu.list.title");
    public static final StringLangEntry MENU_LIST_ENTRY_NAME = new StringLangEntry("menu.list.entry_name");
    public static final StringLangEntry MENU_LIST_ENTRY_WORLD = new StringLangEntry("menu.list.entry_world");
    public static final StringLangEntry MENU_LIST_ENTRY_CHUNKS = new StringLangEntry("menu.list.entry_chunks");
    public static final StringLangEntry MENU_LIST_ENTRY_OWNER = new StringLangEntry("menu.list.entry_owner");
    public static final StringLangEntry MENU_LIST_EMPTY = new StringLangEntry("menu.list.empty");
    public static final StringLangEntry MENU_LIST_CLAIM_HERE_NAME = new StringLangEntry("menu.list.claim_here_name");
    public static final StringLangEntry MENU_LIST_CLAIM_HERE_LORE = new StringLangEntry("menu.list.claim_here_lore");

    public static final StringLangEntry MENU_DETAIL_TITLE = new StringLangEntry("menu.detail.title");
    public static final StringLangEntry MENU_DETAIL_INFO_NAME = new StringLangEntry("menu.detail.info_name");
    public static final StringLangEntry MENU_DETAIL_INFO_OWNER = new StringLangEntry("menu.detail.info_owner");
    public static final StringLangEntry MENU_DETAIL_INFO_WORLD = new StringLangEntry("menu.detail.info_world");
    public static final StringLangEntry MENU_DETAIL_INFO_CHUNKS = new StringLangEntry("menu.detail.info_chunks");
    public static final StringLangEntry MENU_DETAIL_INFO_CREATED = new StringLangEntry("menu.detail.info_created");
    public static final StringLangEntry MENU_DETAIL_FLAGS_NAME = new StringLangEntry("menu.detail.flags_name");
    public static final StringLangEntry MENU_DETAIL_FLAGS_LORE = new StringLangEntry("menu.detail.flags_lore");
    public static final StringLangEntry MENU_DETAIL_MEMBERS_NAME = new StringLangEntry("menu.detail.members_name");
    public static final StringLangEntry MENU_DETAIL_MEMBERS_LORE = new StringLangEntry("menu.detail.members_lore");
    public static final StringLangEntry MENU_DETAIL_BANK_NAME = new StringLangEntry("menu.detail.bank_name");
    public static final StringLangEntry MENU_DETAIL_BANK_LORE = new StringLangEntry("menu.detail.bank_lore");
    public static final StringLangEntry MENU_DETAIL_UNCLAIM_NAME = new StringLangEntry("menu.detail.unclaim_name");
    public static final StringLangEntry MENU_DETAIL_UNCLAIM_LORE = new StringLangEntry("menu.detail.unclaim_lore");
    public static final StringLangEntry MENU_DETAIL_RENAME_NAME = new StringLangEntry("menu.detail.rename_name");
    public static final StringLangEntry MENU_DETAIL_RENAME_LORE = new StringLangEntry("menu.detail.rename_lore");
    public static final StringLangEntry MENU_DETAIL_RENAME_PROMPT = new StringLangEntry("menu.detail.rename_prompt");
    public static final StringLangEntry MENU_DETAIL_TRANSFER_NAME = new StringLangEntry("menu.detail.transfer_name");
    public static final StringLangEntry MENU_DETAIL_TRANSFER_LORE = new StringLangEntry("menu.detail.transfer_lore");
    public static final StringLangEntry MENU_DETAIL_TRANSFER_PROMPT = new StringLangEntry("menu.detail.transfer_prompt");

    public static final StringLangEntry MENU_ROLE_TITLE = new StringLangEntry("menu.role.title");
    public static final StringLangEntry MENU_ROLE_OWNER_NAME = new StringLangEntry("menu.role.owner_name");
    public static final StringLangEntry MENU_ROLE_OWNER_LORE = new StringLangEntry("menu.role.owner_lore");
    public static final StringLangEntry MENU_ROLE_MANAGER_NAME = new StringLangEntry("menu.role.manager_name");
    public static final StringLangEntry MENU_ROLE_MANAGER_LORE = new StringLangEntry("menu.role.manager_lore");
    public static final StringLangEntry MENU_ROLE_MEMBER_NAME = new StringLangEntry("menu.role.member_name");
    public static final StringLangEntry MENU_ROLE_MEMBER_LORE = new StringLangEntry("menu.role.member_lore");
    public static final StringLangEntry MENU_ROLE_VISITOR_NAME = new StringLangEntry("menu.role.visitor_name");
    public static final StringLangEntry MENU_ROLE_VISITOR_LORE = new StringLangEntry("menu.role.visitor_lore");
    public static final StringLangEntry MENU_ROLE_NATURAL_NAME = new StringLangEntry("menu.role.natural_name");
    public static final StringLangEntry MENU_ROLE_NATURAL_LORE = new StringLangEntry("menu.role.natural_lore");

    public static final StringLangEntry MENU_FLAG_TITLE = new StringLangEntry("menu.flag.title");
    public static final StringLangEntry MENU_FLAG_CURRENT = new StringLangEntry("menu.flag.current");
    public static final StringLangEntry MENU_FLAG_DEFAULT = new StringLangEntry("menu.flag.default_line");
    public static final StringLangEntry MENU_FLAG_HINT = new StringLangEntry("menu.flag.hint");
    public static final StringLangEntry MENU_FLAG_STATE_ALLOW = new StringLangEntry("menu.flag.state_allow");
    public static final StringLangEntry MENU_FLAG_STATE_DENY = new StringLangEntry("menu.flag.state_deny");
    public static final StringLangEntry MENU_FLAG_STATE_DEFAULT = new StringLangEntry("menu.flag.state_default");
    public static final StringLangEntry MENU_FLAG_ID_PLACE = new StringLangEntry("menu.flag.id.place");
    public static final StringLangEntry MENU_FLAG_ID_BREAK = new StringLangEntry("menu.flag.id.break");
    public static final StringLangEntry MENU_FLAG_ID_CONTAINER = new StringLangEntry("menu.flag.id.container");
    public static final StringLangEntry MENU_FLAG_ID_DOOR = new StringLangEntry("menu.flag.id.door");
    public static final StringLangEntry MENU_FLAG_ID_REDSTONE = new StringLangEntry("menu.flag.id.redstone");
    public static final StringLangEntry MENU_FLAG_ID_CRAFTING = new StringLangEntry("menu.flag.id.crafting");
    public static final StringLangEntry MENU_FLAG_ID_VEHICLE = new StringLangEntry("menu.flag.id.vehicle");
    public static final StringLangEntry MENU_FLAG_ID_ANIMAL = new StringLangEntry("menu.flag.id.animal");
    public static final StringLangEntry MENU_FLAG_ID_DISPLAY = new StringLangEntry("menu.flag.id.display");
    public static final StringLangEntry MENU_FLAG_ID_PLANTING = new StringLangEntry("menu.flag.id.planting");
    public static final StringLangEntry MENU_FLAG_ID_HARVEST = new StringLangEntry("menu.flag.id.harvest");
    public static final StringLangEntry MENU_FLAG_ID_ITEM = new StringLangEntry("menu.flag.id.item");
    public static final StringLangEntry MENU_FLAG_ID_BANK = new StringLangEntry("menu.flag.id.bank");
    public static final StringLangEntry MENU_FLAG_ID_PVP = new StringLangEntry("menu.flag.id.pvp");
    public static final StringLangEntry MENU_FLAG_ID_EXPLOSION = new StringLangEntry("menu.flag.id.explosion");
    public static final StringLangEntry MENU_FLAG_ID_FIRE_SPREAD = new StringLangEntry("menu.flag.id.fire_spread");
    public static final StringLangEntry MENU_FLAG_ID_FLUID_FLOW = new StringLangEntry("menu.flag.id.fluid_flow");
    public static final StringLangEntry MENU_FLAG_ID_PISTON = new StringLangEntry("menu.flag.id.piston");
    public static final StringLangEntry MENU_FLAG_ID_MOB_SPAWN = new StringLangEntry("menu.flag.id.mob_spawn");
    public static final StringLangEntry MENU_FLAG_ID_MOB_GRIEF = new StringLangEntry("menu.flag.id.mob_grief");
    public static final StringLangEntry MENU_FLAG_ID_TRAMPLE = new StringLangEntry("menu.flag.id.trample");

    public static final StringLangEntry MENU_MEMBERS_TITLE = new StringLangEntry("menu.members.title");
    public static final StringLangEntry MENU_MEMBERS_ENTRY_ROLE = new StringLangEntry("menu.members.entry_role");
    public static final StringLangEntry MENU_MEMBERS_EMPTY = new StringLangEntry("menu.members.empty");
    public static final StringLangEntry MENU_MEMBERS_HINT = new StringLangEntry("menu.members.hint");

    public static final StringLangEntry MENU_BANK_TITLE = new StringLangEntry("menu.bank.title");
    public static final StringLangEntry MENU_BANK_BALANCE_NAME = new StringLangEntry("menu.bank.balance_name");
    public static final StringLangEntry MENU_BANK_BALANCE_LORE = new StringLangEntry("menu.bank.balance_lore");
    public static final StringLangEntry MENU_BANK_DEPOSIT_NAME = new StringLangEntry("menu.bank.deposit_name");
    public static final StringLangEntry MENU_BANK_DEPOSIT_LORE = new StringLangEntry("menu.bank.deposit_lore");
    public static final StringLangEntry MENU_BANK_WITHDRAW_NAME = new StringLangEntry("menu.bank.withdraw_name");
    public static final StringLangEntry MENU_BANK_WITHDRAW_LORE = new StringLangEntry("menu.bank.withdraw_lore");

    public static final StringLangEntry MENU_FAIL_NOT_MANAGER = new StringLangEntry("menu.fail.not_manager");
    public static final StringLangEntry MENU_FAIL_NOT_STANDING = new StringLangEntry("menu.fail.not_standing");

}
