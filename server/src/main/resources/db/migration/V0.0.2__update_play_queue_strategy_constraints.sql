ALTER TABLE public.play_queue
    DROP CONSTRAINT play_queue_strategy_check,
    ADD CONSTRAINT play_queue_strategy_check CHECK (
        playback_strategy IN ('SEQUENTIAL', 'SHUFFLE', 'SINGLE')
        );

ALTER TABLE public.play_queue
    DROP CONSTRAINT play_queue_stop_strategy_check,
    ADD CONSTRAINT play_queue_stop_strategy_check CHECK (
        stop_strategy IN ('TRACK', 'LIST', 'NEVER')
        );
