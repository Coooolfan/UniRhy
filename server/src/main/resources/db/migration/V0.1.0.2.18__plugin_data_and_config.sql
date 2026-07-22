ALTER TABLE public.plugin
    ADD COLUMN config_definition JSONB NOT NULL DEFAULT '{
      "schema": {
        "type": "object",
        "properties": {},
        "required": [],
        "additionalProperties": false
      },
      "order": []
    }'::jsonb,
    ADD CONSTRAINT ck_plugin_config_definition CHECK (
        jsonb_typeof(config_definition) = 'object'
        AND config_definition ? 'schema'
        AND jsonb_typeof(config_definition -> 'schema') = 'object'
        AND config_definition ? 'order'
        AND jsonb_typeof(config_definition -> 'order') = 'array'
    );

ALTER TABLE public.plugin
    ALTER COLUMN config_definition DROP DEFAULT;

CREATE TABLE public.plugin_data
(
    plugin_id       TEXT  NOT NULL REFERENCES public.plugin (id) ON DELETE CASCADE,
    key             TEXT  NOT NULL,
    value           JSONB,
    encrypted_value BYTEA,

    PRIMARY KEY (plugin_id, key),

    CONSTRAINT ck_plugin_data_value
        CHECK (num_nonnulls(value, encrypted_value) = 1)
);
