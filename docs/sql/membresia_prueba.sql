-- Ejecuta una vez en SQL Developer conectado como HR.
-- Crea/actualiza el plan de demostración. No procesa pagos.
-- El plan básico BAS1 ya debe existir para permitir cancelar la prueba.

MERGE INTO HR.MEMBRESIAS destino
USING (
    SELECT 'PRO1' AS ID_TIPOS,
           'Plus de prueba' AS NOMBRE_TIPOS,
           400000 AS LIMITE_TOKENS
    FROM DUAL
) origen
ON (destino.ID_TIPOS = origen.ID_TIPOS)
WHEN MATCHED THEN
    UPDATE SET NOMBRE_TIPOS = origen.NOMBRE_TIPOS,
               LIMITE_TOKENS = origen.LIMITE_TOKENS
WHEN NOT MATCHED THEN
    INSERT (ID_TIPOS, NOMBRE_TIPOS, LIMITE_TOKENS)
    VALUES (origen.ID_TIPOS, origen.NOMBRE_TIPOS, origen.LIMITE_TOKENS);

COMMIT;

-- Comprobación opcional:
SELECT ID_TIPOS, NOMBRE_TIPOS, LIMITE_TOKENS
FROM HR.MEMBRESIAS
WHERE ID_TIPOS IN ('BAS1', 'PRO1')
ORDER BY ID_TIPOS;
