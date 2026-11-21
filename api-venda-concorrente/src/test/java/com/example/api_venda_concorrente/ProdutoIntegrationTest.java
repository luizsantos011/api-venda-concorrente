package com.example.api_venda_concorrente;

import com.example.api_venda_concorrente.model.Produto;
import com.example.api_venda_concorrente.repository.ProdutoRepository;
import com.example.api_venda_concorrente.service.ProdutoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
public class ProdutoIntegrationTest {

    @Autowired
    private ProdutoService service;

    @Autowired
    private ProdutoRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void deveImpedirVendaDuplicadaDoUltimoItem() throws InterruptedException {
        Produto produto = new Produto("PlayStation 5", 1);
        produto = repository.save(produto);
        Long produtoId = produto.getId();

        int totalCompradores = 2;
        ExecutorService executor = Executors.newFixedThreadPool(totalCompradores);
        CountDownLatch largada = new CountDownLatch(1);
        CountDownLatch fim = new CountDownLatch(totalCompradores);

        AtomicInteger comprasSucesso = new AtomicInteger(0);
        AtomicInteger comprasFalha = new AtomicInteger(0);

        for (int i = 0; i < totalCompradores; i++) {
            executor.submit(() -> {
                try {
                    largada.await();
                    service.realizarVenda(produtoId, 1);
                    comprasSucesso.incrementAndGet();
                } catch (IllegalStateException e) {
                    comprasFalha.incrementAndGet();
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    fim.countDown();
                }
            });
        }

        largada.countDown();
        fim.await();

        assertEquals(1, comprasSucesso.get(), "Apenas 1 cliente deve conseguir comprar o último item");
        assertEquals(1, comprasFalha.get(), "O outro cliente deve receber erro de estoque insuficiente");

        Produto produtoAtualizado = repository.findById(produtoId).orElseThrow();
        assertEquals(0, produtoAtualizado.getQuantidadeEstoque(), "O estoque final deve ser rigorosamente 0");
    }
}