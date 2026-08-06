package dev.catgirlyannick.catclans.storage;

import dev.catgirlyannick.catclans.model.Clan;
import dev.catgirlyannick.catclans.model.ClanInvite;
import dev.catgirlyannick.catclans.model.ClanRole;
import dev.catgirlyannick.catclans.model.BattlepassProgress;
import dev.catgirlyannick.catclans.model.BattlepassReward;
import dev.catgirlyannick.catclans.model.BattlepassRewardType;
import dev.catgirlyannick.catclans.model.ClanUnlocks;
import dev.catgirlyannick.catclans.model.ClanRankingStats;
import dev.catgirlyannick.catclans.model.ClanHome;
import dev.catgirlyannick.catclans.model.ClanWarResult;
import dev.catgirlyannick.catclans.model.DailyLoginState;
import dev.catgirlyannick.catclans.model.DiplomacyRequest;
import dev.catgirlyannick.catclans.model.DiplomacyType;
import dev.catgirlyannick.catclans.model.DiplomacyView;
import dev.catgirlyannick.catclans.model.RewardClaimResult;
import dev.catgirlyannick.catclans.model.RankingKillResult;

import java.time.Instant;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Map;
import java.util.Set;

public interface ClanRepository extends AutoCloseable {

    void initialize() throws Exception;

    Optional<Clan> findById(UUID clanId) throws Exception;

    Optional<Clan> findByMember(UUID playerId) throws Exception;

    Optional<Clan> findByNormalizedName(String normalizedName) throws Exception;

    Optional<Clan> findByNormalizedTag(String normalizedTag) throws Exception;

    Optional<Clan> findByNameOrTag(String normalizedSearch) throws Exception;

    List<Clan> findAll() throws Exception;

    void save(Clan clan) throws Exception;

    boolean deleteClan(UUID clanId) throws Exception;

    void saveAndDeleteInvitesForPlayer(Clan clan, UUID playerId) throws Exception;

    Optional<ClanInvite> findInvite(UUID clanId, UUID playerId) throws Exception;

    List<ClanInvite> findInvitesForPlayer(UUID playerId, java.time.Instant now) throws Exception;

    void saveInvite(ClanInvite invite) throws Exception;

    void deleteInvite(UUID clanId, UUID playerId) throws Exception;

    void deleteInvitesForPlayer(UUID playerId) throws Exception;

    Map<UUID, List<ClanRole>> findAllRoles() throws Exception;

    List<ClanRole> findRoles(UUID clanId) throws Exception;

    void saveRole(ClanRole role) throws Exception;

    void deleteRole(UUID clanId, String roleId) throws Exception;

    Map<String, Boolean> findRolePermissions(UUID clanId, String roleId) throws Exception;

    void setRolePermission(
            UUID clanId,
            String roleId,
            String permission,
            boolean allowed
    ) throws Exception;

    Map<String, Boolean> findMemberPermissions(UUID clanId, UUID playerId) throws Exception;

    void setMemberPermission(
            UUID clanId,
            UUID playerId,
            String permission,
            Boolean allowed
    ) throws Exception;

    int findRoleLimit(UUID clanId, int fallback) throws Exception;

    void saveRoleLimit(UUID clanId, int maximumRoles) throws Exception;

    Map<UUID, BattlepassProgress> findAllBattlepassProgress() throws Exception;

    BattlepassProgress findBattlepassProgress(UUID clanId, Instant now) throws Exception;

    void saveBattlepassProgress(BattlepassProgress progress) throws Exception;

    Optional<DailyLoginState> findDailyLoginState(UUID playerId) throws Exception;

    void saveDailyLoginState(DailyLoginState state) throws Exception;

    void saveDailyLoginAndBattlepass(
            DailyLoginState state,
            BattlepassProgress progress
    ) throws Exception;

    Optional<Instant> findPvpRewardTime(UUID victimId) throws Exception;

    void savePvpRewardAndBattlepass(
            UUID victimId,
            Instant rewardedAt,
            BattlepassProgress progress
    ) throws Exception;

    List<BattlepassReward> findBattlepassRewards(int fromLevel, int toLevel)
            throws Exception;

    void saveBattlepassReward(BattlepassReward reward) throws Exception;

    void deleteBattlepassReward(int level, BattlepassRewardType type) throws Exception;

    Set<String> findClaimedRewardKeys(UUID clanId, int fromLevel, int toLevel)
            throws Exception;

    RewardClaimResult claimBattlepassReward(
            UUID clanId,
            UUID ownerId,
            BattlepassReward reward,
            int absoluteMaxMembers,
            int absoluteMaxRoles,
            int absoluteMaxVaultPages,
            int absoluteMaxBonusHomeSlots,
            int defaultMaxRoles
    ) throws Exception;

    ClanUnlocks findClanUnlocks(UUID clanId) throws Exception;

    Map<UUID, ClanRankingStats> findAllRankingStats() throws Exception;

    boolean recordDailyRankingActivity(UUID clanId, LocalDate activityDate)
            throws Exception;

    RankingKillResult recordRankingKill(
            UUID killerClanId,
            UUID victimClanId,
            UUID victimId,
            Instant occurredAt,
            Instant cooldownCutoff
    ) throws Exception;

    List<ClanWarResult> finalizeExpiredWars(Instant now) throws Exception;

    Optional<ClanWarResult> endActiveWar(
            UUID firstClanId,
            UUID secondClanId,
            Instant endedAt
    ) throws Exception;

    Map<Integer, byte[]> findVaultPage(UUID clanId, int page) throws Exception;

    void saveVaultSlot(UUID clanId, int page, int slot, byte[] itemData) throws Exception;

    BigDecimal findBankBalance(UUID clanId) throws Exception;

    Optional<BigDecimal> depositBank(
            UUID clanId,
            BigDecimal amount,
            Instant updatedAt
    ) throws Exception;

    Optional<BigDecimal> withdrawBank(
            UUID clanId,
            BigDecimal amount,
            Instant updatedAt
    ) throws Exception;

    BigDecimal restoreBankBalance(
            UUID clanId,
            BigDecimal amount,
            Instant updatedAt
    ) throws Exception;

    List<ClanHome> findHomes(UUID clanId) throws Exception;

    Optional<ClanHome> findHome(UUID clanId, int number) throws Exception;

    void saveHome(ClanHome home) throws Exception;

    boolean deleteHome(UUID clanId, int number) throws Exception;

    DiplomacyView findDiplomacyView(
            UUID viewerClanId,
            UUID targetClanId,
            Instant now
    ) throws Exception;

    Optional<DiplomacyRequest> findDiplomacyRequest(UUID requestId) throws Exception;

    List<DiplomacyRequest> findIncomingDiplomacyRequests(
            UUID targetClanId,
            DiplomacyType type,
            Instant now
    ) throws Exception;

    int countPendingDiplomacyRequests(UUID clanId, Instant now) throws Exception;

    boolean saveDiplomacyRequest(DiplomacyRequest request) throws Exception;

    void declineDiplomacyRequest(UUID requestId) throws Exception;

    void acceptDiplomacyRequest(
            DiplomacyRequest request,
            UUID acceptedBy,
            Instant acceptedAt
    ) throws Exception;

    @Override
    void close() throws Exception;
}
