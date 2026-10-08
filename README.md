## 1. Arquitetura e Comunicação BLE

* **Dispositivo de Rádio:** Placa Nordic Thingy:91X configurada em modo *Connectivity Bridge*, anunciando como periférico BLE.
* **Nome do Dispositivo (Advertising):** `LTM_Tag`
* **Perfil BLE:** Nordic UART Service (NUS)
* **Service UUID:** `6E400001-B5A3-F393-E0A9-E50E24DCCA9E`
* **RX Characteristic (Notificação da Tag para a App):** `6E400003-B5A3-F393-E0A9-E50E24DCCA9E`
* **CCCD (Descritor de Ativação):** `00002902-0000-1000-8000-00805f9b34fb`


* **Prioridade de Conexão:** No iOS, deve ser solicitado o perfil de baixa latência (`CBConnectionEventMatchingOption` ou equivalente no CoreBluetooth) para garantir que a taxa de pacotes (~10 Hz / 20 Hz decuada no firmware do dispositivo) seja entregue sem engarrafamento no buffer.

---

## 2. Estrutura do Pacote de Dados (Payload)

A placa envia continuamente uma string codificada em UTF-8 com **10 valores decimais separados por vírgula**, terminando em quebra de linha (`\n`):

$$\text{pos}[0], \text{pos}[1], \text{pos}[2], q_0, q_1, q_2, q_3, \text{Roll}, \text{Pitch}, \text{Yaw}$$

* **Índices [0..2]:** Translação linear estimada $X, Y, Z$ (em metros).
* **Índices [3..6]:** Quaternião de orientação normalizado ($W, X, Y, Z$).
* **Índices [7..9]:** Ângulos de Euler em graus. São esses os valores monitorados para medir a execução do exercício:
    * `[7]` $\rightarrow$ **Roll**
    * `[8]` $\rightarrow$ **Pitch**
    * `[9]` $\rightarrow$ **Yaw**



---

## 3. Máquina de Estados e Calibração Oculta

O utilizador apenas clica em **"Iniciar Treino"**. A primeira repetição física do exercício atua como calibração invisível sem intervenção manual.

### Estados do Exercício

1. **`IDLE` (Parado):** Aplicação aguarda o comando de início.
2. **`ADVANCING_REP1` (Primeira Subida/Avanço):**
    * O algoritmo guarda os ângulos iniciais (`startAngles`).
    * Mede a variação angular ($\Delta$) de Roll, Pitch e Yaw durante o movimento.
    * O eixo com maior deslocamento ($\Delta > 15^\circ$) é selecionado automaticamente como o **Eixo Ativo**.
    * Ao atingir a amplitude máxima e recuar mais de $8^\circ$ (limiar de histerese), o sistema detecta a reversão de sentido, regista o instante temporal `returnStartTime` e passa para `RETURNING_REP1`.
    * Inversão Dinâmica: Se $\text{startAngle} > \text{peakAngle}$, define `isInverted = true` (necessário para informar ao cálculo que a variação da leitura dos angulos será invertida devido ao movimento contrário do aparelho).


3. **`RETURNING_REP1` (Primeira Descida/Retorno):**
    * Acompanha o retorno à posição de partida.
    * Ao atingir o ponto de partida e avançar novamente mais de $8^\circ$ (início da 2ª repetição), calcula o tempo total de retorno:

    $$\Delta t = \text{currentTime} - \text{returnStartTime}$$

    * Com o tempo total de retorno, é possível calcular a velocidade ideal da senoide, que será determinada através da duração da onda (o dobro do tempo de retorno):


    $$\text{waveDurationMillis} = 2 \times \Delta t \quad (\text{limitado entre } 1000\text{ ms e } 10000\text{ ms})$$


    * Atribui `repCount = 1` (primeira repetição finalizada), ativa `isTracking = true` e transita para `TRACKING`.


4. **`TRACKING` (Gamificação e Contagem Ativa):**
    * Normaliza a posição angular atual entre $0.0$ (base/repouso) e $1.0$ (pico):

    $$\text{rawNorm} = \frac{\text{currentVal} - \text{minVal}}{\text{maxVal} - \text{minVal}}$$


    $$\text{normalizedPosition} = \begin{cases} 1.0 - \text{rawNorm}, & \text{se } isInverted \\ \text{rawNorm}, & \text{caso contrário} \end{cases}$$


    * **Contagem de Repetições Concluídas:** Uma repetição é somada quando a bolinha ultrapassa $80\%$ de amplitude (`normalizedPosition > 0.80`) e regressa abaixo de $20\%$ (`normalizedPosition < 0.20`).



---

## 4. Motor de Física da Onda Senoidal (Malha Fechada)

A onda não é uma animação passiva; ela ajusta a sua velocidade em tempo real para sincronizar com o ritmo do atleta.

* **Eixo de Coordenadas:** Na interface, a bolinha move-se verticalmente num $X$ fixo ($40\text{ dp}$ da borda esquerda).
* **Parâmetros Base:**
    * $\text{Frequência da Onda} = 1.5$ picos visíveis no ecrã.
    * $\text{Tolerância de Desvio} = 0.30$ ($30\%$ de erro antes de saturar velocidade).
    * $\text{Velocidade Mínima} = 0.0\times$ (a onda para completamente para esperar o atleta).
    * $\text{Velocidade Máxima} = 2.5\times$ (a onda acelera para alcançar o atleta).



### Lógica de Compensação Temporal

A cada quadro renderizado:

1. Calcula o ângulo da onda na coordenada $X$ da bolinha:

$$\theta = (\text{normX} \times 2\pi \times \text{Frequência}) + \text{wavePhase}$$


2. Posição normalizada da onda na mesma escala da bolinha ($0.0 = \text{base}, 1.0 = \text{topo}$):

$$\text{waveNormY} = 0.5 - 0.5 \times \sin(\theta)$$


3. Direção da onda via derivada temporal:

$$\text{isWaveGoingUp} = (-0.5 \times \cos(\theta)) > 0$$


4. Comparação de avanço:

$$\text{signedError} = \text{animatedY} - \text{waveNormY}$$


$$\text{isBallAhead} = \begin{cases} \text{signedError} > 0, & \text{se a onda estiver a subir} \\ \text{signedError} < 0, & \text{se a onda estiver a descer} \end{cases}$$


5. Modulação de fase:

$$\text{speedMult} = \begin{cases} 1.0 + \left(\frac{\vert{}\text{error}\vert{}}{\text{Tolerância}}\right) \times (\text{Velocidade Máxima} - 1.0), & \text{se a bolinha estiver adiantada} \\ 1.0 - \left(\frac{\vert{}\text{error}\vert{}}{\text{Tolerância}}\right), & \text{se a bolinha estiver atrasada} \end{cases}$$



---

## 5. Gamificação (Coletáveis e Placar)

* **Pontos Coletáveis:** Posicionados nos ângulos múltiplos de $90^\circ$ ($\frac{\pi}{2}$) da onda senoidal:
* Pico superior $\rightarrow Y = 1.0$
* Vale inferior $\rightarrow Y = 0.00$
* Meio $\rightarrow Y = 0.50$


* **Margem de Tolerância de Acerto:** $\pm 15\%$ ($0.15$) em relação à altura do ponto.
* **Mecânica de Colisão:**
    * Quando a fase angular ultrapassa o centro horizontal da bolinha, compara-se $\vert{}\text{animatedY} - \text{targetY}\vert{} \le 0.15$.
    * **Acerto:** O ponto fica **Verde** e soma $+10$ pontos ao placar.
    * **Falha:** O ponto fica **Vermelho** e não pontua.