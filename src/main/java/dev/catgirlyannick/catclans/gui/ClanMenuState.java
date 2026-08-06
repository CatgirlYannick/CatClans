package dev.catgirlyannick.catclans.gui;

import java.util.UUID;

record ClanMenuState(
        ClanMenuType type,
        UUID clanId,
        UUID targetId,
        int page,
        String firstValue,
        String secondValue,
        ConfirmAction action
) {

    static ClanMenuState from(ClanMenuHolder holder) {
        return new ClanMenuState(
                holder.type(),
                holder.clanId(),
                holder.targetId(),
                holder.page(),
                holder.firstValue(),
                holder.secondValue(),
                holder.action()
        );
    }
}
