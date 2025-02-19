/*
 * Copyright @ 2018 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jitsi.meet.test;

import org.jitsi.meet.test.base.*;
import org.jitsi.meet.test.util.*;
import org.jitsi.meet.test.web.*;
import org.testng.*;
import org.testng.annotations.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.*;

/**
 * @author Damian Minkov
 * @author Boris Grozev
 */
public class MalleusJitsificus
    extends WebTestBase
{

    /**
     * The config fragment which enables P2P test mode.
     */
//    public final static String MANUAL_P2P_MODE_FRAGMENT
//            = "config.testing.p2pTestMode=true";

    /**
     * The video file to use as input for the first participant (the sender).
     */
    private static final String INPUT_VIDEO_FILE
//        = "resources/FakeVideoStream.y4m";
            = "resources/FourPeople_1280x720_30.y4m";

    public static final String CONFERENCES_PNAME
        = "org.jitsi.malleus.conferences";
    public static final String PARTICIPANTS_PNAME
        = "org.jitsi.malleus.participants";
    public static final String SENDERS_PNAME
        = "org.jitsi.malleus.senders";
    public static final String AUDIO_SENDERS_PNAME
        = "org.jitsi.malleus.audio_senders";
//    public static final String ENABLE_P2P_PNAME
//        = "org.jitsi.malleus.enable_p2p";
    public static final String DURATION_PNAME
        = "org.jitsi.malleus.duration";
    public static final String ROOM_NAME_PREFIX_PNAME
        = "org.jitsi.malleus.room_name_prefix";
    public static final String REGIONS_PNAME
        = "org.jitsi.malleus.regions";
    public static final String USE_NODE_TYPES_PNAME
        = "org.jitsi.malleus.use_node_types";
    public static final String MAX_DISRUPTED_BRIDGES_PCT_PNAME
        = "org.jitsi.malleus.max_disrupted_bridges_pct";
    public static final String USE_LOAD_TEST_PNAME
        = "org.jitsi.malleus.use_load_test";
    public static final String JOIN_DELAY_PNAME
        = "org.jitsi.malleus.join_delay";
    public static final String SWITCH_SPEAKERS
        = "org.jitsi.malleus.switch_speakers";
    public static final String USE_STAGE_VIEW
        = "org.jitsi.malleus.use_stage_view";
    public static final String JWT
        = "org.jitsi.malleus.jwt";
    public static final String P2P_ENABLED
            = "org.jitsi.malleus.p_to_p_enabled";

    private final Phaser allHungUp = new Phaser();

    private CountDownLatch bridgeSelectionCountDownLatch;

    // The participant threads put the IP of their bridge in this set.
    private final Set<String> bridgeSelection = ConcurrentHashMap.newKeySet();

    // The test thread creates this set.
    // The participant threads check if the IP of their bridge is in this set.
    private Set<String> bridgesToFail;

    @DataProvider(name = "dp", parallel = true)
    public Object[][] createData(ITestContext context)
    {
        // If the tests is not in the list of tests to be executed,
        // skip executing the DataProvider.
        if (isSkipped())
        {
            return new Object[0][0];
        }

        int numConferences = Integer.parseInt(System.getProperty(CONFERENCES_PNAME));
        int numParticipants = Integer.parseInt(System.getProperty(PARTICIPANTS_PNAME));
        String numSendersStr = System.getProperty(SENDERS_PNAME);
        int numSenders = numSendersStr == null
            ? numParticipants
            : Integer.parseInt(numSendersStr);

        String numAudioSendersStr = System.getProperty(AUDIO_SENDERS_PNAME);
        int numAudioSenders = numAudioSendersStr == null
                ? numParticipants
                : Integer.parseInt(numAudioSendersStr);

        int durationMs = 1000 * Integer.parseInt(System.getProperty(DURATION_PNAME));

        int joinDelayMs = Integer.parseInt(System.getProperty(JOIN_DELAY_PNAME));

        String[] regions = null;
        String regionsStr = System.getProperty(REGIONS_PNAME);
        if (regionsStr != null && !"".equals(regionsStr))
        {
            regions = regionsStr.split(",");
        }

        String roomNamePrefix = System.getProperty(ROOM_NAME_PREFIX_PNAME);
        if (roomNamePrefix == null)
        {
            roomNamePrefix = "anvil-";
        }

//        String enableP2pStr = System.getProperty(ENABLE_P2P_PNAME);
//        boolean enableP2p
//            = enableP2pStr == null || Boolean.parseBoolean(enableP2pStr);

//        boolean enableP2p = false; // RD 4/12/21, no peer to peer for now

        float maxDisruptedBridges = 0;
        String maxDisruptedBridgesStr = System.getProperty(MAX_DISRUPTED_BRIDGES_PCT_PNAME);
        if (!"".equals(maxDisruptedBridgesStr))
        {
            maxDisruptedBridges = Float.parseFloat(maxDisruptedBridgesStr);
        }

        boolean useLoadTest = Boolean.parseBoolean(System.getProperty(USE_LOAD_TEST_PNAME));

        boolean switchSpeakers = Boolean.parseBoolean(System.getProperty(SWITCH_SPEAKERS));

        boolean useStageView = Boolean.parseBoolean(System.getProperty(USE_STAGE_VIEW));

        // Use one thread per conference.
        context.getCurrentXmlTest().getSuite()
            .setDataProviderThreadCount(numConferences);

        String p2p_enabled = System.getProperty(P2P_ENABLED);

        print("p2p_enabled is: " + p2p_enabled);

        if(p2p_enabled.toLowerCase().equals("true")){
            p2p_enabled = "config.p2p.enabled=true";
        } else {
            p2p_enabled = "config.p2p.enabled=false";
        }

        print("will run with:");
        print("conferences="+ numConferences);
        print("participants=" + numParticipants);
        print("senders=" + numSenders);
        print("audio senders=" + numAudioSenders + (switchSpeakers ? " (switched)" : ""));
        print("duration=" + durationMs + "ms");
        print("join delay=" + joinDelayMs + "ms");
        print("room_name_prefix=" + roomNamePrefix);
        print("enable_p2p=" + p2p_enabled);
        print("max_disrupted_bridges_pct=" + maxDisruptedBridges);
        print("regions=" + (regions == null ? "null" : Arrays.toString(regions)));
        print("stage view=" + useStageView);

        String jwt = System.getProperty(JWT);
        jwt = "jwt=" + jwt;



        Object[][] ret = new Object[numConferences][4];
        for (int i = 0; i < numConferences; i++)
        {
            String roomName = roomNamePrefix + i;
            JitsiMeetUrl url
                = participants.getJitsiMeetUrl()
                .setRoomName(roomName)
                // XXX I don't remember if/why these are needed.
                    .appendConfig("config.p2p.useStunTurn=true")
                    .appendConfig("config.disable1On1Mode=false")
                    .appendConfig("config.testing.noAutoPlayVideo=false") // 4/20/21 WAS true, setting to false helps with getting video to play
                    .appendConfig("config.pcStatsInterval=10000")
//                    .appendConfig("config.p2p.enabled=" + (enableP2p ? "true" : "false"))
//                    .appendConfig("config.p2p.enabled=false") // 4/22/21, can set to false to force JVB connections only
//                    .appendConfig("config.p2p.enabled=true")
                    .appendConfig(p2p_enabled)
//                    .appendConfig(config.testing.p2pTestMode=true)
                    .appendConfig(jwt, false);

            if (useStageView)
                url.appendConfig("config.disableTileView=true");

            if (useLoadTest)
            {
                url.setServerUrl(url.getServerUrl() + "/_load-test");
            }

            ret[i] = new Object[] {
                url, numParticipants, durationMs, joinDelayMs, numSenders, numAudioSenders, regions, maxDisruptedBridges,
                switchSpeakers
            };
        }

        return ret;
    }

    @Test(dataProvider = "dp")
    public void testMain(
        JitsiMeetUrl url, int numberOfParticipants,
        long durationMs, long joinDelayMs, int numSenders, int numAudioSenders,
        String[] regions, float blipMaxDisruptedPct, boolean switchSpeakers)
        throws Exception
    {
        List<MalleusTask> malleusTasks = new ArrayList<>(numberOfParticipants);

        bridgeSelectionCountDownLatch = new CountDownLatch(numberOfParticipants);

        ScheduledExecutorService pool = Executors.newScheduledThreadPool(numberOfParticipants + 2);

        boolean disruptBridges = blipMaxDisruptedPct > 0;
        for (int i = 0; i < numberOfParticipants; i++)
        {
            MalleusTask task = new MalleusTask(
                i,
                url.copy(),
                durationMs,
                i * joinDelayMs,
                i >= numSenders /* no video */,
                switchSpeakers || i >= numAudioSenders /* no audio */,
                regions == null ? null : regions[i % regions.length],
                disruptBridges
            );
            malleusTasks.add(task);
            task.start(pool);
        }

        List<Future<?>> otherTasks = new ArrayList<>();

        if (disruptBridges)
        {
            otherTasks.add(pool.submit(() -> {
                    try
                    {
                        disruptBridges(blipMaxDisruptedPct, durationMs / 1000);
                    }
                    catch (Exception e)
                    {
                        // Let it be returned by Future#get()
                        throw new RuntimeException(e);
                    }
                }
            ));
        }

        if (switchSpeakers)
        {
            otherTasks.add(pool.submit(() -> {
                    try
                    {
                        switchSpeakers(malleusTasks, durationMs + joinDelayMs * numberOfParticipants, numAudioSenders);
                    }
                    catch (Exception e)
                    {
                        // Let it be returned by Future#get()
                        throw new RuntimeException(e);
                    }
                }
            ));
        }

        List<Throwable> errors = new ArrayList<>();

        for (MalleusTask t: malleusTasks)
        {
            try
            {
                t.waitUntilComplete();
            }
            catch (ExecutionException e)
            {
                errors.add(e.getCause());
            }
        }

        for (Future<?> t: otherTasks)
        {
            try
            {
                t.get();
            }
            catch (ExecutionException e)
            {
                errors.add(e.getCause());
            }
        }

        if (!errors.isEmpty())
        {
            for(int i = 0; i < errors.size(); i++){
                print("ERROR #" + i + " ---> " + errors.get(i).toString());
            }
            throw new Exception("Failed with multiple errors. Throws the primary.", errors.get(0));
        }
    }

    private class MalleusTask
    {
        private final int i;
        private final JitsiMeetUrl _url;
        private final long durationMs;
        private final long joinDelayMs;
        private final boolean muteVideo;
        private boolean muteAudio;
        private final boolean enableFailureDetection;

        public boolean running;
        public boolean spoken;

        private Future<?> started;
        private Future<?> complete;
        private ScheduledFuture<?> checking;

        WebParticipant participant;
        private String bridge;

        private ScheduledExecutorService pool;

        public MalleusTask(
            int i, JitsiMeetUrl url, long durationMs, long joinDelayMs,
            boolean muteVideo, boolean muteAudio, String region,
            boolean enableFailureDetection)
        {
            this.i = i;
            this._url = url;
            this.durationMs = durationMs;
            this.joinDelayMs = joinDelayMs;
            this.muteVideo = muteVideo;
            this.muteAudio = muteAudio;
            this.enableFailureDetection = enableFailureDetection;

            if (muteVideo)
            {
                _url.appendConfig("config.startWithVideoMuted=true");
            }
            if (muteAudio)
            {
                _url.appendConfig("config.startWithAudioMuted=true");
            }

            if (region != null)
            {
                _url.appendConfig("config.deploymentInfo.userRegion=\"" + region + "\"");
            }
        }

        public void start(ScheduledExecutorService pool)
        {
            this.pool = pool;

            started = pool.schedule(this::join, joinDelayMs, TimeUnit.MILLISECONDS);
            complete = pool.schedule(this::finish, joinDelayMs + durationMs, TimeUnit.MILLISECONDS);

            if (enableFailureDetection)
            {
                long healthCheckIntervalMs = 5000; // Configure this?

                checking = pool.scheduleWithFixedDelay(this::check,
                    joinDelayMs + healthCheckIntervalMs, healthCheckIntervalMs, TimeUnit.MILLISECONDS);
            }
        }

        private void join()
        {
            boolean useLoadTest = Boolean.parseBoolean(System.getProperty(USE_LOAD_TEST_PNAME));
            boolean useNodeTypes = Boolean.parseBoolean(System.getProperty(USE_NODE_TYPES_PNAME));

            WebParticipantOptions ops
                = new WebParticipantOptions()
                .setFakeStreamVideoFile(INPUT_VIDEO_FILE)
                .setLoadTest(useLoadTest);

            if (useNodeTypes)
            {
                if (muteVideo)
                {
                    /* TODO: is it okay to have an audio sender use a malleus receiver ep? */
                    ops.setApplicationName("malleusReceiver");
                }
                else
                {
                    ops.setApplicationName("malleusSender");
                }
            }

            participant = participants.createParticipant("web.participant" + (i + 1), ops);
            allHungUp.register();
            try
            {
                participant.joinConference(_url);
            }
            catch (Exception e)
            {
                /* If join failed, don't block other threads from hanging up. */
                allHungUp.arriveAndDeregister();
                /* If join failed, don't block bridge disruption. */
                bridgeSelectionCountDownLatch.countDown();
                complete.cancel(true);
                throw e;
            }

            try
            {
                if (enableFailureDetection)
                {
                    bridge = participant.getBridgeIp();
                    bridgeSelection.add(bridge);
                }
            }
            catch (Exception e)
            {
                /* If we fail to fetch the bridge ip, don't block other threads from hanging up. */
                allHungUp.arriveAndDeregister();
                complete.cancel(true);
                if (checking != null) {
                    checking.cancel(true);
                }
                throw e;
            }
            finally
            {
                bridgeSelectionCountDownLatch.countDown();
            }

            running = true;
        }

        private void finish()
        {
            running = false;
            if (checking != null)
            {
                checking.cancel(true);
            }

            try
            {
                participant.hangUp();
            }
            catch (Exception e)
            {
                TestUtils.print("Exception hanging up " + participant.getName());
                e.printStackTrace();
            }
            try
            {
                /* There seems to be a Selenium or chrome webdriver bug where closing one parallel
                 * Chrome session can cause another one to close too.  So wait for all sessions
                 * to hang up before we close any of them.
                 */
                allHungUp.arriveAndAwaitAdvance();
                MalleusJitsificus.this.closeParticipant(participant);
            }
            catch (Exception e)
            {
                TestUtils.print("Exception closing " + participant.getName());
                e.printStackTrace();
            }
        }

        public void waitUntilComplete() throws ExecutionException, InterruptedException
        {
            started.get();
            complete.get();
        }

        private void check()
        {
            try
            {
                participant.waitForIceConnected(0 /* no timeout */);
            }
            catch (Exception ex)
            {
                TestUtils.print("Participant " + i + " is NOT connected.");
                if (!bridgesToFail.contains(bridge))
                {
                    throw ex;
                }
                else
                {
                    // wait for reconnect
                    participant.waitForIceConnected(20);
                    TestUtils.print("Participant " + i + " reconnected.");
                }
            }
        }

        public void muteAudio(boolean mute)
        {
            pool.execute(() -> doMuteAudio(mute));
        }

        private void doMuteAudio(boolean mute)
        {
            if (mute != muteAudio)
            {
                participant.muteAudio(mute);
                muteAudio = mute;
                if (mute)
                {
                    TestUtils.print("Muted participant " + i);
                }
                else
                {
                    TestUtils.print("Unmuted participant " + i);
                    spoken = true;
                }
            }
        }
    }

    private void disruptBridges(float blipMaxDisruptedPct, long durationInSeconds)
        throws Exception
    {
        bridgeSelectionCountDownLatch.await();

        bridgesToFail = Collections.synchronizedSet(bridgeSelection.stream()
            .limit((long) (Math.ceil(bridgeSelection.size() * blipMaxDisruptedPct / 100)))
            .collect(Collectors.toSet()));

        Blip.failFor(durationInSeconds).theseBridges(bridgesToFail).call();
    }

    private static final double SINGLE_TALK_MS = 854;
    private static final double DOUBLE_TALK_MS = 226;
    private static final double SILENCE_MS = 456;

    private static final double P_SILENCE = 0.4;

    private static long getDuration(double t)
    {
        return (long)(-t * (Math.log(1 - ThreadLocalRandom.current().nextDouble())));
    }

    /** Randomly switch speakers.
     *  This is modeled on ITU-T P.59, but choosing among N speakers rather than just 2.
     *  (At most 2 at a time.)
     */
    private void switchSpeakers(List<MalleusTask> malleusTasks, long durationInMs, int numAudioSenders)
        throws InterruptedException
    {
        List<MalleusTask> currentSpeakers = new ArrayList<>();
        long remainingTime = durationInMs;

        while (remainingTime > 0)
        {
            switch (currentSpeakers.size())
            {
            case 0:
            {
                /* No speakers - add a speaker. */
                MalleusTask newSpeaker = chooseSpeaker(malleusTasks, currentSpeakers, numAudioSenders);
                if (newSpeaker != null)
                {
                    newSpeaker.muteAudio(false);
                    currentSpeakers.add(newSpeaker);
                }
                break;
            }

            case 1:
            {
                /* One speaker - either add or remove a speaker. */
                if (ThreadLocalRandom.current().nextDouble() < P_SILENCE)
                {
                    MalleusTask removedSpeaker = currentSpeakers.get(0);
                    removedSpeaker.muteAudio(true);
                    currentSpeakers.remove(removedSpeaker);
                }
                else
                {
                    MalleusTask newSpeaker = chooseSpeaker(malleusTasks, currentSpeakers, numAudioSenders);
                    if (newSpeaker != null)
                    {
                        newSpeaker.muteAudio(false);
                        currentSpeakers.add(newSpeaker);
                    }
                }
                break;
            }

            default:
            {
                /* More than one speaker - remove a speaker. */
                int idx = ThreadLocalRandom.current().nextInt(currentSpeakers.size());
                MalleusTask removedSpeaker = currentSpeakers.get(idx);
                removedSpeaker.muteAudio(true);
                currentSpeakers.remove(removedSpeaker);
                break;
            }
            }

            long duration;
            switch (currentSpeakers.size())
            {
            case 0:
            {
                /* Silence */
                duration = 0;
                while (duration < 200)
                {
                    duration += getDuration(SILENCE_MS);
                }
                break;
            }
            case 1:
            {
                /* Single-talk */
                duration = getDuration(SINGLE_TALK_MS);
                break;
            }
            default:
            {
                /* Double-talk */
                duration = getDuration(DOUBLE_TALK_MS);
                break;
            }
            }

            long sleepTime = Math.min(duration, remainingTime);
            remainingTime -= sleepTime;
            Thread.sleep(sleepTime);
        }
    }

    /** Randomly choose an active MalleusTask to be the next speaker.
     * If there are N past speakers we choose a past speaker with probability (N / (N + 1))
     * and some other conference member with probability (1 / (N + 1)), unless everyone
     * is a past speaker.
     */
    private MalleusTask chooseSpeaker(List<MalleusTask> tasks, List<MalleusTask> currentSpeakers, int numAudioSenders)
    {
        List<MalleusTask> pastSpeakers = tasks.stream().
            limit(numAudioSenders).
            filter((t) -> t.running).
            filter((t) -> t.spoken).
            filter((t) -> !currentSpeakers.contains(t)).
            collect(Collectors.toList());

        List<MalleusTask> nonSpeakers = tasks.stream().
            limit(numAudioSenders).
            filter((t) -> t.running).
            filter((t) -> !t.spoken).
            filter((t) -> !currentSpeakers.contains(t)).
            collect(Collectors.toList());

        if (pastSpeakers.isEmpty() && nonSpeakers.isEmpty())
        {
            return null;
        }

        int randMax = pastSpeakers.size() + (nonSpeakers.isEmpty() ? 0 : 1);
        int idx = ThreadLocalRandom.current().nextInt(randMax);
        if (idx < pastSpeakers.size())
        {
            return pastSpeakers.get(idx);
        }
        int idx2 = ThreadLocalRandom.current().nextInt(nonSpeakers.size());
        return nonSpeakers.get(idx2);
    }

    private void print(String s)
    {
        System.err.println(s);
    }

    /**
     * {@inheritDoc}
     */
    public boolean skipTestByDefault()
    {
        // Skip by default.
        return true;
    }
}
