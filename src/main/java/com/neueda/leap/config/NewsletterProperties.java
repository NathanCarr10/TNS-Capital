package com.neueda.leap.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.ZoneId;

/**
 * Settings for the weekly market newsletter (newsletter.* in application.yml).
 *
 * @param cron              when the scheduled job publishes the week's issue; "-" switches it off
 * @param zone              time zone of the cron and of the trading week (the exchange's)
 * @param moversPerSide     how many gainers and how many losers an issue lists
 * @param headlinesPerMover how many news headlines are shown under each mover at most
 * @param earlyPublish      whether support staff may publish the week's issue before the weekly job,
 *                          e.g. for a demo; the job then skips that week. Off in production
 */
@ConfigurationProperties(prefix = "newsletter")
public record NewsletterProperties(String cron, ZoneId zone, int moversPerSide, int headlinesPerMover,
        boolean earlyPublish) {
}
