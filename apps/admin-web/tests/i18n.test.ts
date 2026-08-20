import { describe, expect, it } from 'vitest';
import { localizedCapability, localizedRole, normalizeLocale, routeLabel, translate, translateText } from '../src/i18n';

describe('church-instance i18n', () => {
    it('normalizes configured language tags and falls back safely', () => {
        expect(normalizeLocale('es-MX')).toBe('es');
        expect(normalizeLocale('pt-BR')).toBe('pt');
        expect(normalizeLocale('fr-FR')).toBe('en');
    });

    it('translates shared admin copy and navigation labels from the configured locale', () => {
        expect(translate('es-MX', 'brand')).toBe('Administración de Cadentia');
        expect(routeLabel('pt-BR', 'Instance settings')).toBe('Configurações da instância');
    });

    it('translates role, capability, and page phrases from the church locale', () => {
        expect(localizedRole('es-GT', 'CATALOG_EDITOR')).toBe('Editor del catálogo');
        expect(localizedRole('es-GT', 'catalog.admin.approve')).toBe('Aprobación del catálogo');
        expect(localizedRole('es-GT', 'role.integration_manager')).toBe('Administrador de integraciones');
        expect(localizedCapability('es-GT', 'REVIEW_CATALOG')).toBe('Revisar catálogo');
        expect(translateText('es-GT', 'Import review snapshot')).toBe('Resumen de revisión de importaciones');
    });

    it('translates admin route controls, raw enum values, and dynamic copy', () => {
        expect(translateText('es-GT', 'Open full import review queue')).toBe('Abrir la cola completa de revisión de importaciones');
        expect(translateText('es-GT', 'praise; grace; advent')).toBe('alabanza; gracia; adviento');
        expect(translateText('es-GT', 'Psalm 23; Romans 8')).toBe('Salmo 23; Romanos 8');
        expect(translateText('es-GT', 'PUBLIC_DOMAIN')).toBe('Dominio público');
        expect(translateText('es-GT', 'PLANNING_CENTER_RESOURCE')).toBe('Recurso de Planning Center');
        expect(translateText('es-GT', 'IMPORT_BATCH')).toBe('Lote de importación');
        expect(translateText('es-GT', 'entityType')).toBe('tipo de entidad');
        expect(translateText('es-GT', 'CONNECTED')).toBe('Conectado');
        expect(translateText('es-GT', 'Configured; secret redacted')).toBe('Configurado; secreto redactado');
        expect(translateText('es-GT', ' catalog songs. Page ')).toBe(' canciones del catálogo. Página ');
        expect(translateText('es-GT', ' server-matched candidates. Page ')).toBe(' candidatos coincidentes del servidor. Página ');
        expect(translateText('es-GT', 'APPROVED')).toBe('Aprobado');
        expect(translateText('es-GT', 'OPEN')).toBe('Abierto');
        expect(translateText('es-GT', 'CREATE_NEW')).toBe('Crear nueva');
        expect(translateText('es-GT', 'Chordpro')).toBe('ChordPro');
        expect(translateText('es-GT', 'IMPORT_CANDIDATE')).toBe('Candidato de importación');
        expect(translateText('es-GT', 'DOCTRINAL, PROVENANCE')).toBe('Doctrinal, Procedencia');
        expect(translateText('es-GT', 'en')).toBe('Inglés');
    });
});
