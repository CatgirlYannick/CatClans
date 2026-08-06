package dev.catgirlyannick.catclans.service;

import dev.catgirlyannick.catclans.model.Clan;
import dev.catgirlyannick.catclans.model.ClanWarResult;

public record AdminWarEndResult(
        Clan firstClan,
        Clan secondClan,
        ClanWarResult result
) {
}
