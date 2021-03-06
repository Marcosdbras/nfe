package com.fincatto.documentofiscal.mdfe3.webservices;

import com.fincatto.documentofiscal.DFUnidadeFederativa;
import com.fincatto.documentofiscal.assinatura.AssinaturaDigital;
import com.fincatto.documentofiscal.mdfe3.MDFeConfig;
import com.fincatto.documentofiscal.mdfe3.classes.MDFAutorizador3;
import com.fincatto.documentofiscal.mdfe3.classes.nota.evento.MDFeDetalhamentoEvento;
import com.fincatto.documentofiscal.mdfe3.classes.nota.evento.MDFeEnviaEventoEncerramento;
import com.fincatto.documentofiscal.mdfe3.classes.nota.evento.MDFeInfoEvento;
import com.fincatto.documentofiscal.mdfe3.classes.nota.evento.MDFeRetorno;
import com.fincatto.documentofiscal.mdfe3.classes.nota.evento.MDFeEvento;
import com.fincatto.documentofiscal.mdfe3.classes.nota.evento.MDFeProtocoloEvento;
import com.fincatto.documentofiscal.mdfe3.classes.parsers.MDFChaveParser;
import com.fincatto.documentofiscal.mdfe3.webservices.recepcaoevento.MDFeRecepcaoEventoStub;
import com.fincatto.documentofiscal.persister.DFPersister;
import com.fincatto.documentofiscal.validadores.BigDecimalParser;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.util.AXIOMUtil;
import org.joda.time.DateTime;
import org.joda.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;

/**
 * Created by Eldevan Nery Junior on 17/11/17.
 */
class WSEncerramento {
    private static final String DESCRICAO_EVENTO = "Encerramento";
    private static final BigDecimal VERSAO_LEIAUTE = new BigDecimal("3.00");
    private static final String EVENTO_ENCERRAMENTO = "110112";
    private static final Logger LOGGER = LoggerFactory.getLogger(WSEncerramento.class);
    private final MDFeConfig config;

    WSEncerramento(final MDFeConfig config) {
        this.config = config;
    }

    MDFeRetorno encerramentoMdfeAssinado(final String chaveAcesso, final String eventoAssinadoXml) throws Exception {
        final OMElement omElementResult = this.efetuaEncerramento(eventoAssinadoXml, chaveAcesso);
        return new DFPersister().read(MDFeRetorno.class, omElementResult.toString());
    }

    MDFeRetorno encerraMdfe(final String chaveAcesso, final String numeroProtocolo
            , final String codigoMunicipio, final LocalDate dataEncerramento, final DFUnidadeFederativa unidadeFederativa) throws Exception {
        final String encerramentoNotaXML = this.gerarDadosEncerramento(chaveAcesso, numeroProtocolo, codigoMunicipio, dataEncerramento, unidadeFederativa).toString();
        final String xmlAssinado = new AssinaturaDigital(this.config).assinarDocumento(encerramentoNotaXML);
        final OMElement omElementResult = this.efetuaEncerramento(xmlAssinado, chaveAcesso);
        return new DFPersister().read(MDFeRetorno.class, omElementResult.toString());
    }

    private OMElement efetuaEncerramento(final String xmlAssinado, final String chaveAcesso) throws Exception {
        final MDFChaveParser mdfChaveParser = new MDFChaveParser(chaveAcesso);
        final MDFeRecepcaoEventoStub.MdfeCabecMsg cabec = new MDFeRecepcaoEventoStub.MdfeCabecMsg();
        cabec.setCUF(mdfChaveParser.getNFUnidadeFederativa().getCodigoIbge());
        cabec.setVersaoDados( BigDecimalParser.tamanho5Com2CasasDecimais(VERSAO_LEIAUTE, "Versao do Evento"));

        final MDFeRecepcaoEventoStub.MdfeCabecMsgE cabecE = new MDFeRecepcaoEventoStub.MdfeCabecMsgE();
        cabecE.setMdfeCabecMsg(cabec);

        final MDFeRecepcaoEventoStub.MdfeDadosMsg dados = new MDFeRecepcaoEventoStub.MdfeDadosMsg();
        final OMElement omElementXML = AXIOMUtil.stringToOM(xmlAssinado);
        WSEncerramento.LOGGER.debug(omElementXML.toString());
        dados.setExtraElement(omElementXML);

        WSEncerramento.LOGGER.info(cabec.toString());

        final MDFAutorizador3 autorizador = MDFAutorizador3.valueOfCodigoUF(mdfChaveParser.getNFUnidadeFederativa());
        final String urlWebService =  autorizador.getMDFeRecepcaoEvento(this.config.getAmbiente());
        if (urlWebService == null) {
            throw new IllegalArgumentException("Nao foi possivel encontrar URL para RecepcaoEvento "
                    + mdfChaveParser.getModelo().name() + ", autorizador " + autorizador.name());
        }
        MDFeRecepcaoEventoStub.MdfeRecepcaoEventoResult mdfeRecepcaoEventoResult = new MDFeRecepcaoEventoStub(urlWebService).mdfeRecepcaoEvento(dados, cabecE);
        final OMElement omElementResult = mdfeRecepcaoEventoResult.getExtraElement();
        WSEncerramento.LOGGER.debug(omElementResult.toString());
        return omElementResult;
    }

    private MDFeProtocoloEvento gerarDadosEncerramento(final String chaveAcesso, final String numeroProtocolo
            , final String codigoMunicipio, final LocalDate dataEncerramento, final DFUnidadeFederativa unidadeFederativa) {
        final MDFChaveParser chaveParser = new MDFChaveParser(chaveAcesso);
        final MDFeEnviaEventoEncerramento encerramento = new MDFeEnviaEventoEncerramento();
        encerramento.setDescricaoEvento(WSEncerramento.DESCRICAO_EVENTO);
        encerramento.setCodigoMunicipio(codigoMunicipio);
        encerramento.setDataEncerramento(dataEncerramento);
        encerramento.setUf(unidadeFederativa);
        encerramento.setProtocoloAutorizacao(numeroProtocolo);
        final MDFeInfoEvento infoEvento = new MDFeInfoEvento();
        infoEvento.setAmbiente(this.config.getAmbiente());
        infoEvento.setChave(chaveAcesso);
        infoEvento.setCnpj(chaveParser.getCnpjEmitente());
        infoEvento.setDataHoraEvento(DateTime.now());
        infoEvento.setId(String.format("ID%s%s0%s", WSEncerramento.EVENTO_ENCERRAMENTO, chaveAcesso, "1"));
        infoEvento.setNumeroSequencialEvento(1);
        infoEvento.setOrgao(chaveParser.getNFUnidadeFederativa().getCodigo());
        infoEvento.setCodigoEvento(WSEncerramento.EVENTO_ENCERRAMENTO);

        MDFeDetalhamentoEvento mdFeDetalhamentoEvento = new MDFeDetalhamentoEvento();
        mdFeDetalhamentoEvento.setEnviaEventoEncerramento(encerramento);
        mdFeDetalhamentoEvento.setVersaoEvento(WSEncerramento.VERSAO_LEIAUTE);
        infoEvento.setDetEvento(mdFeDetalhamentoEvento);

        MDFeEvento mdfeEventoEncerramento = new MDFeEvento();
        mdfeEventoEncerramento.setInfoEvento(infoEvento);
        mdfeEventoEncerramento.setVersao(WSEncerramento.VERSAO_LEIAUTE);

        MDFeProtocoloEvento mdfeProtocoloEventoCancelamento = new MDFeProtocoloEvento();
        mdfeProtocoloEventoCancelamento.setVersao(WSEncerramento.VERSAO_LEIAUTE);
        mdfeProtocoloEventoCancelamento.setEvento(mdfeEventoEncerramento);

        return mdfeProtocoloEventoCancelamento;
    }
}
