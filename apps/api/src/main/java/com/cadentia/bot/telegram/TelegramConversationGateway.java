package com.cadentia.bot.telegram;

public interface TelegramConversationGateway {

    TelegramAdapterResponse start(TelegramChannelEvent event);

    TelegramAdapterResponse help(TelegramChannelEvent event);

    TelegramAdapterResponse newSetlist(TelegramChannelEvent event);

    TelegramAdapterResponse status(TelegramChannelEvent event);

    TelegramAdapterResponse cancel(TelegramChannelEvent event);

    TelegramAdapterResponse settings(TelegramChannelEvent event);

    TelegramAdapterResponse text(TelegramChannelEvent event);

    TelegramAdapterResponse menuSelection(TelegramChannelEvent event);

    TelegramAdapterResponse requestAccess(TelegramChannelEvent event);
}
