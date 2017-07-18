package com.sberbank.pfm.test.concordion.extensions.exam.bootstrap;

import org.concordion.api.Resource;
import org.concordion.api.extension.ConcordionExtender;
import org.concordion.api.extension.ConcordionExtension;

public class BootstrapExtension implements ConcordionExtension {

    @Override
    public void addTo(ConcordionExtender e) {
        e.withLinkedCSS("/bootstrap/bootstrap.css", new Resource("/bootstrap/bootstrap.css"));
        e.withLinkedCSS("/bootstrap/enable-bootstrap.css", new Resource("/bootstrap/enable-bootstrap.css"));
    }
}