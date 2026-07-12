ALTER TABLE public.play_queue
    DROP CONSTRAINT play_queue_strategy_check;

ALTER TABLE public.play_queue
    ADD CONSTRAINT play_queue_strategy_check CHECK (
        playback_strategy IN ('SEQUENTIAL', 'SHUFFLE', 'SINGLE', 'RADIO')
        );
